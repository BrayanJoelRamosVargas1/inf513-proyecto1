package bo.edu.uagrm.inf513.grupo09sa.persistencia;

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
        sql(sqlEsquema);
        sql(sqlEsquema);
        var repo = new RepositorioPostgres(datos);
        assertTrue(repo.correoProcesado("historial"));
        assertEquals(1, numero("select count(*) from p1_correo_control where mensaje_id='historial' and admitido_en is null"));
        assertEquals(0, repo.promoverDiferidos(25));
        assertEquals(RepositorioPostgres.Admision.DUPLICADO, repo.registrarEntrada("historial","ensayo@example.com","AYUDA[]"));
    }

    @Test void postgresValidaOfertaAreasYAsignacionDelEvaluador() throws Exception {
        sql("""
            insert into p1_usuario(registro,nombres,apellidos,rol,correo) values
                ('1','Ana','Ensayo','POSTULANTE','ana@example.com'),('2','Eva','Ensayo','EVALUADOR','eva@example.com');
            insert into p1_convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas)
                values('ENSAYO','Ensayo',current_date,current_date,'ABIERTA',2);
            insert into p1_cargo(nombre) values('Ofertado'),('No ofertado');
            insert into p1_oferta(convocatoria_id,cargo_id,plazas) values(1,1,2);
            """);
        invalido("insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,2)");
        sql("insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,1)");
        long post = numero("select id from p1_postulacion");
        sql("insert into p1_area_examen values(1,1,'teoria',70)");
        invalido("insert into p1_area_examen values(1,1,'practica',70)");
        sql("insert into p1_area_examen values(1,1,'practica',30)");
        sql("insert into p1_asignacion_evaluador(postulacion_id,evaluador_id,tipo) values("+post+",2,'EXAMEN')");
        String examen = "insert into p1_examen(postulacion_id,area,puntaje,evaluador_correo) values("+post+",";
        invalido(examen + "'inventada',85,'eva@example.com')");
        invalido(examen + "'teoria',85,'sin-cuenta@example.com')");
        sql(examen + "'teoria',85,'eva@example.com')");
        assertEquals(1, numero("select count(*) from p1_examen"));
        invalido("update p1_area_examen set peso=60 where area='teoria'");
        invalido("delete from p1_area_examen where area='practica'");
        invalido("insert into p1_area_examen values(1,1,'oral',10)");
        assertEquals(100, numero("select sum(peso) from p1_area_examen"));
        assertEquals(70, numero("select peso from p1_area_examen where area='teoria'"));
    }

    @Test void unExamenGeneralImpideCambiarEsaCategoriaAExamenPonderado() throws Exception {
        prepararOfertas();
        sql("""
            insert into p1_usuario(registro,nombres,apellidos,rol,correo) values
                ('1','Ana','Ensayo','POSTULANTE','ana@example.com'),('2','Eva','Ensayo','EVALUADOR','eva@example.com');
            insert into p1_oferta values(1,1,1),(1,2,1);
            insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id) values(1,1,1);
            insert into p1_asignacion_evaluador(postulacion_id,evaluador_id,tipo) values(1,2,'MERITOS');
            insert into p1_area_examen values(1,2,'practica',100);
            """);
        String notaGeneral="insert into p1_examen(postulacion_id,puntaje,evaluador_correo) values(1,85,'eva@example.com')";
        invalido(notaGeneral);
        sql("update p1_asignacion_evaluador set tipo='EXAMEN' where postulacion_id=1");
        sql(notaGeneral);
        invalido("insert into p1_area_examen values(1,1,'teoria',100)");
        invalido("update p1_area_examen set cargo_id=1 where cargo_id=2");
        assertEquals(1, numero("select count(*) from p1_area_examen where cargo_id=2"));
    }

    @Test void validaLosCuposAlEditarOfertasYConvocatoriasDirectamente() throws Exception {
        prepararOfertas();
        sql("insert into p1_oferta values(1,1,1)");
        invalido("insert into p1_oferta values(1,2,2)");
        sql("insert into p1_oferta values(1,1,2) on conflict(convocatoria_id,cargo_id) do update set plazas=excluded.plazas");
        invalido("update p1_convocatoria set plazas=1 where id=1");
        invalido("update p1_oferta set plazas=3 where convocatoria_id=1");
        sql("update p1_oferta set plazas=1 where convocatoria_id=1");
        sql("update p1_convocatoria set plazas=1 where id=1");
        assertEquals(1, numero("select sum(plazas) from p1_oferta"));
        assertEquals(1, numero("select plazas from p1_convocatoria where id=1"));
    }

    @Test void unaTransaccionConSnapshotAnteriorNoPuedeSobreofertar() throws Exception {
        prepararOfertas();
        sql("update p1_convocatoria set plazas=1 where id=1");
        try (var conexion = datos.getConnection(); var sentencia = conexion.createStatement()) {
            conexion.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conexion.setAutoCommit(false);
            try (var resultado = sentencia.executeQuery("select count(*) from p1_oferta")) {
                assertTrue(resultado.next());
                assertEquals(0, resultado.getInt(1));
            }
            sql("insert into p1_oferta values(1,1,1)");
            assertEquals("40001", assertThrows(SQLException.class,
                    () -> sentencia.execute("insert into p1_oferta values(1,2,1)")).getSQLState());
            conexion.rollback();
        }
        invalido("insert into p1_oferta values(1,2,1)");
        assertEquals(1, numero("select sum(plazas) from p1_oferta"));
    }

    private void prepararOfertas() throws SQLException {
        sql("""
            insert into p1_convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas)
                values('CUPOS','Cupos de ensayo',current_date,current_date,'ABIERTA',2);
            insert into p1_cargo(nombre) values('Teoría'),('Práctica');
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
