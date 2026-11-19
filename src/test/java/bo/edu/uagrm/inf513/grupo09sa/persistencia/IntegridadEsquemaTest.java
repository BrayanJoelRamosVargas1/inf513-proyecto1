package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class IntegridadEsquemaTest {
    private final PGSimpleDataSource datos = new PGSimpleDataSource();
    private final String esquema = "integridad_" + UUID.randomUUID().toString().replace("-", "");
    private String sqlEsquema;

    @BeforeEach void preparar() throws Exception {
        datos.setURL(System.getenv("TEST_DB_URL"));
        datos.setUser(System.getenv().getOrDefault("TEST_DB_USER", "audit"));
        datos.setPassword(System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        sql("create schema " + esquema);
        datos.setCurrentSchema(esquema);
        sqlEsquema = Files.readString(Path.of("database/schema.sql"));
        sql(sqlEsquema);
    }

    private void migrar() throws Exception {
        var directorio = Path.of("database/migrations");
        sql(Files.readString(directorio.resolve("001_preparar.sql")) + "\n" + sqlEsquema + "\n"
                + Files.readString(directorio.resolve("002_migrar_historial.sql")));
    }

    @AfterEach void limpiar() throws Exception {
        sql("drop schema " + esquema + " cascade");
    }

    @Test void importaHistoricosSinDuplicarlosNiConsumirCupo() throws Exception {
        assertEquals(0, numero("select count(*) from pg_tables where schemaname=current_schema() and tablename='p1_correo_procesado'"));
        sql("""
            create table p1_correo_procesado (
                mensaje_id varchar(255) primary key, remitente varchar(160) not null,
                asunto text, exito boolean not null, procesado_en timestamptz not null
            );
            insert into p1_correo_procesado values('historial','historico@local','BASELINE',true,now());
            """);
        migrar();
        migrar();
        var repo = new RepositorioPostgres(datos);
        assertTrue(repo.correoProcesado("historial"));
        assertEquals(1, numero("select count(*) from correo_control where mensaje_id='historial' and admitido_en is null"));
        assertEquals(0, repo.promoverDiferidos(25));
        assertEquals(RepositorioPostgres.Admision.DUPLICADO, repo.registrarEntrada("historial","ensayo@example.com","AYUDA[]"));
    }

    @Test void consolidaConfirmacionesPendientesConsumidasYResultadosAnteriores() throws Exception {
        assertEquals(17, numero("select count(*) from pg_tables where schemaname=current_schema()"));
        sql("""
            create table p1_confirmacion (
                mensaje_id text primary key, remitente text not null, token_hash text unique not null,
                orden_json text not null, vence_en timestamptz not null, confirmacion_id text, usado boolean not null
            );
            create table p1_resultado_orden (
                mensaje_id text primary key, exito boolean not null, respuesta_asunto text not null,
                respuesta_cuerpo text not null, ejecutado_en timestamptz not null
            );
            insert into correo_control(mensaje_id,remitente,estado) values
                ('pendiente','prueba@example.com','RESPONDIDO'), ('consumida','prueba@example.com','RESPONDIDO');
            insert into p1_confirmacion values
                ('pendiente','prueba@example.com',encode(sha256(convert_to(repeat('A',43),'UTF8')),'hex'),
                 '{"comando":"AYUDA","parametros":[]}',now()+interval '10 minutes',null,false),
                ('consumida','prueba@example.com',encode(sha256(convert_to(repeat('B',43),'UTF8')),'hex'),
                 '{"comando":"AYUDA","parametros":[]}',now()-interval '10 minutes','confirmada-antes',true);
            insert into p1_resultado_orden values
                ('consulta-anterior',true,'Consulta','Resultado original',now()),
                ('consumida',true,'Anterior','Resultado secundario',now()),
                ('confirmacion:'||encode(sha256(convert_to('consumida','UTF8')),'hex'),
                 false,'Error','Resultado de la confirmación anterior',now());
            """);
        migrar();

        var repo = new RepositorioPostgres(datos);
        var anterior = repo.confirmarOrden("confirmada-antes","prueba@example.com","B".repeat(43),
                (id,orden) -> fail("La orden ya consumida no se repite"));
        assertFalse(anterior.exito());
        assertEquals("Resultado de la confirmación anterior",anterior.cuerpo());
        assertEquals("Resultado original",repo.ejecutarIdempotente("consulta-anterior","prueba@example.com",null).cuerpo());
        var nueva = repo.confirmarOrden("confirmada-ahora","prueba@example.com","A".repeat(43),
                (id,orden) -> Resultado.ok("Confirmada después de migrar"));
        assertTrue(nueva.exito());
        migrar();
        assertEquals(nueva,repo.confirmarOrden("confirmada-ahora","prueba@example.com","A".repeat(43),
                (id,orden) -> fail("La migración no debe reactivar un código consumido")));
        assertFalse(repo.confirmarOrden("otra-confirmacion","prueba@example.com","A".repeat(43),
                (id,orden) -> fail("Otro correo no puede reutilizar el código")).exito());
        assertEquals(3,numero("select count(*) from correo_control where resultado_exito is not null"));
        assertEquals(0,repo.promoverDiferidos(25));
    }

    @Test void todasLasTablasSeConectanYCadaMigracionConservaElBuzonAnterior() throws Exception {
        assertEquals(17, numero("select count(*) from pg_tables where schemaname=current_schema()"));
        assertEquals(21, numero("select count(*) from pg_constraint where contype='f' and connamespace=current_schema()::regnamespace"));
        assertEquals(17, numero("""
            with recursive uniones as (
                select conrelid origen,confrelid destino from pg_constraint
                where contype='f' and connamespace=current_schema()::regnamespace
                union all
                select confrelid,conrelid from pg_constraint
                where contype='f' and connamespace=current_schema()::regnamespace
            ), conectadas(tabla) as (
                select 'buzon'::regclass::oid
                union
                select u.destino from conectadas c join uniones u on u.origen=c.tabla
            )
            select count(*) from conectadas
            """));
        sql("""
            create table p1_servicio_estado (
                clave text primary key, valor text not null, actualizado_en timestamptz not null default now()
            );
            insert into p1_servicio_estado values ('bandeja_inicializada_pop3','true',now());
            insert into persona values ('501','Ana','Test','Estudiante','30001','70001','persona@example.com');
            insert into auditoria(actor,accion,entidad,entidad_id)
                 values ('prueba@example.com','INSPER','INSPER','501');
            insert into correo_control(mensaje_id,remitente,estado)
                 values ('roundcube:INBOX:1','prueba@example.com','RESPONDIDO');
            """);
        migrar();
        migrar();
        var repo = new RepositorioPostgres(datos);
        assertTrue(repo.servicioInicializado("pop3"));
        assertFalse(repo.servicioInicializado("roundcube"));
        assertEquals(1, numero("select count(*) from correo_control where buzon_protocolo='roundcube'"));
        assertEquals(1, numero("select count(*) from auditoria where persona_ci='501' and mensaje_id is null"));
    }

    @Test void migraElNombreConservandoDatosClavesYLaCola() throws Exception {
        sql("""
            insert into usuario(registro,nombres,apellidos,rol,correo)
                values('101','Ana','Ensayo','POSTULANTE','ana@example.com');
            insert into convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas)
                values('ANTES','Ensayo',current_date,current_date,'ABIERTA',1);
            insert into cargo(nombre) values('Auxiliar');
            insert into postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,1);
            insert into expediente(postulacion_id,descripcion,ruta_archivo,contenido)
                values(1,'Ensayo','postgres:p1_expediente/1',convert_to('%PDF-ensayo','UTF8'));
            insert into correo_control(mensaje_id,remitente,asunto,estado,buzon_protocolo)
                values('antes','ana@example.com','AYUDA[]','DIFERIDO','pop3');
            alter table postulacion rename to p1_postulacion;
            alter table usuario rename to p1_usuario;
            alter table convocatoria rename to p1_convocatoria;
            alter table cargo rename to p1_cargo;
            alter table correo_control rename to p1_correo_control;
            alter table buzon rename to p1_buzon;
            alter table adjunto_correo rename to p1_adjunto_correo;
            alter table area_examen rename to p1_area_examen;
            alter table asignacion_evaluador rename to p1_asignacion_evaluador;
            alter table auditoria rename to p1_auditoria;
            alter table desempate rename to p1_desempate;
            alter table evaluacion_requisito rename to p1_evaluacion_requisito;
            alter table examen rename to p1_examen;
            alter table expediente rename to p1_expediente;
            alter table merito rename to p1_merito;
            alter table oferta rename to p1_oferta;
            """);
        migrar();
        migrar();
        assertEquals(17, numero("select count(*) from pg_tables where schemaname=current_schema()"));
        assertEquals(1, numero("""
            select count(*) from postulacion p join usuario u on u.id=p.postulante_id
            join convocatoria c on c.id=p.convocatoria_id where u.registro='101' and c.codigo='ANTES'
            """));
        assertEquals(1, numero("select count(*) from correo_control c join buzon b on b.protocolo=c.buzon_protocolo where c.mensaje_id='antes' and c.estado='DIFERIDO'"));
        assertEquals(1, numero("select count(*) from expediente where ruta_archivo='postgres:expediente/1' and contenido=convert_to('%PDF-ensayo','UTF8')"));
        assertEquals(21, numero("select count(*) from pg_constraint where contype='f' and connamespace=current_schema()::regnamespace"));
    }

    @Test void moverElEsquemaSeDetieneSiYaExisteUnaTablaHomonima() throws Exception {
        sql("create table p1_usuario(id integer)");
        sql("insert into p1_usuario values(73)");
        var fallo=assertThrows(SQLException.class,this::migrar);
        assertTrue(fallo.getMessage().contains("nombres de destino"));
        assertEquals(73, numero("select id from p1_usuario"));
        assertEquals(0, numero("select count(*) from usuario"));
    }

    @Test void postgresValidaOfertaAreasYAsignacionDelEvaluador() throws Exception {
        sql("""
            insert into usuario(registro,nombres,apellidos,rol,correo) values
                ('1','Ana','Ensayo','POSTULANTE','ana@example.com'),('2','Eva','Ensayo','EVALUADOR','eva@example.com');
            insert into convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas)
                values('ENSAYO','Ensayo',current_date,current_date,'ABIERTA',2);
            insert into cargo(nombre) values('Ofertado'),('No ofertado');
            insert into oferta(convocatoria_id,cargo_id,plazas) values(1,1,2);
            """);
        invalido("insert into postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,2)");
        sql("insert into postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,1)");
        long post = numero("select id from postulacion");
        sql("insert into area_examen values(1,1,'teoria',70)");
        invalido("insert into area_examen values(1,1,'practica',70)");
        sql("insert into area_examen values(1,1,'practica',30)");
        sql("insert into asignacion_evaluador(postulacion_id,evaluador_id,tipo) values("+post+",2,'EXAMEN')");
        String examen = "insert into examen(postulacion_id,area,puntaje,evaluador_id) values("+post+",";
        invalido(examen + "'inventada',85,2)");
        invalido(examen + "'teoria',85,999)");
        sql(examen + "'teoria',85,2)");
        assertEquals(1, numero("select count(*) from examen"));
        invalido("update area_examen set peso=60 where area='teoria'");
        invalido("delete from area_examen where area='practica'");
        invalido("insert into area_examen values(1,1,'oral',10)");
        assertEquals(100, numero("select sum(peso) from area_examen"));
        assertEquals(70, numero("select peso from area_examen where area='teoria'"));
        sql("update oferta set plazas=1 where convocatoria_id=1 and cargo_id=1");
        sql("insert into oferta values (1,2,1)");
        invalido("update postulacion set cargo_id=2 where id=" + post);
        invalido("insert into merito(postulacion_id,criterio,puntaje,evaluador_id) values("
                + post + ",'Experiencia',80,2)");
        invalido("insert into evaluacion_requisito(postulacion_id,requisito,cumple,evaluador_id) values("
                + post + ",'Identidad',true,1)");
        invalido("insert into auditoria(actor,accion,entidad,entidad_id,postulacion_id)"
                + " values('eva@example.com','REGEXA','REGEXA','999'," + post + ")");
    }

    @Test void unExamenGeneralImpideCambiarEsaCategoriaAExamenPonderado() throws Exception {
        prepararOfertas();
        sql("""
            insert into usuario(registro,nombres,apellidos,rol,correo) values
                ('1','Ana','Ensayo','POSTULANTE','ana@example.com'),('2','Eva','Ensayo','EVALUADOR','eva@example.com');
            insert into oferta values(1,1,1),(1,2,1);
            insert into postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,1);
            insert into asignacion_evaluador(postulacion_id,evaluador_id,tipo) values(1,2,'MERITOS');
            insert into area_examen values(1,2,'practica',100);
            """);
        String notaGeneral="insert into examen(postulacion_id,puntaje,evaluador_id) values(1,85,2)";
        invalido(notaGeneral);
        sql("update asignacion_evaluador set tipo='EXAMEN' where postulacion_id=1");
        sql(notaGeneral);
        invalido("insert into area_examen values(1,1,'teoria',100)");
        invalido("update area_examen set cargo_id=1 where cargo_id=2");
        assertEquals(1, numero("select count(*) from area_examen where cargo_id=2"));
    }

    @Test void validaLosCuposAlEditarOfertasYConvocatoriasDirectamente() throws Exception {
        prepararOfertas();
        sql("insert into oferta values(1,1,1)");
        invalido("insert into oferta values(1,2,2)");
        sql("insert into oferta values(1,1,2) on conflict(convocatoria_id,cargo_id) do update set plazas=excluded.plazas");
        invalido("update convocatoria set plazas=1 where id=1");
        invalido("update oferta set plazas=3 where convocatoria_id=1");
        sql("update oferta set plazas=1 where convocatoria_id=1");
        sql("update convocatoria set plazas=1 where id=1");
        assertEquals(1, numero("select sum(plazas) from oferta"));
        assertEquals(1, numero("select plazas from convocatoria where id=1"));
    }

    @Test void unaTransaccionConSnapshotAnteriorNoPuedeSobreofertar() throws Exception {
        prepararOfertas();
        sql("update convocatoria set plazas=1 where id=1");
        try (var conexion = datos.getConnection(); var sentencia = conexion.createStatement()) {
            conexion.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conexion.setAutoCommit(false);
            try (var resultado = sentencia.executeQuery("select count(*) from oferta")) {
                assertTrue(resultado.next());
                assertEquals(0, resultado.getInt(1));
            }
            sql("insert into oferta values(1,1,1)");
            assertEquals("40001", assertThrows(SQLException.class,
                    () -> sentencia.execute("insert into oferta values(1,2,1)")).getSQLState());
            conexion.rollback();
        }
        invalido("insert into oferta values(1,2,1)");
        assertEquals(1, numero("select sum(plazas) from oferta"));
    }

    private void prepararOfertas() throws SQLException {
        sql("""
            insert into convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas)
                values('CUPOS','Cupos de ensayo',current_date,current_date,'ABIERTA',2);
            insert into cargo(nombre) values('Teoría'),('Práctica');
            """);
    }

    private void invalido(String sentencia) {
        assertEquals("23514", assertThrows(SQLException.class, () -> sql(sentencia)).getSQLState());
    }

    private void sql(String sentencia) throws SQLException {
        try (var c = datos.getConnection(); var q = c.createStatement()) {
            q.execute(sentencia);
        }
    }

    private long numero(String sentencia) throws SQLException {
        try (var c = datos.getConnection(); var q = c.createStatement(); var r = q.executeQuery(sentencia)) {
            assertTrue(r.next());
            return r.getLong(1);
        }
    }
}
