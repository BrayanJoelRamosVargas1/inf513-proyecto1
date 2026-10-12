package bo.edu.uagrm.inf513.grupo09sa.comandos;

import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioProyecto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcesadorOrdenTest {
    @Test void permiteCrearPrimerDecanoComoArranque() {
        FakeRepo repo=new FakeRepo(false,false);
        Resultado r=new ProcesadorOrden(new CatalogoComandos(),repo).procesar("admin@gmail.com",new Orden("INSUSU",java.util.List.of("1","Ana","Pérez","DECANO","70000000","admin@gmail.com","true")));
        assertTrue(r.exito());
    }
    @Test void rechazaRolInvalido() {
        Resultado r=new ProcesadorOrden(new CatalogoComandos(),new FakeRepo(false,false)).procesar("a@b.com",new Orden("INSUSU",java.util.List.of("1","Ana","Pérez","JEFE","700","a@b.com","true")));
        assertFalse(r.exito());assertTrue(r.cuerpo().contains("Rol inválido"));
    }
    @Test void exigePermisoDespuesDelArranque() {
        Resultado r=new ProcesadorOrden(new CatalogoComandos(),new FakeRepo(true,false)).procesar("x@gmail.com",new Orden("INSCON",java.util.List.of("C1","Auxiliatura","2026-10-01","2026-10-20","ABIERTA","2")));
        assertFalse(r.exito());assertTrue(r.cuerpo().contains("rol requerido"));
    }
    @Test void validaRangoDePuntaje() {
        Resultado r=new ProcesadorOrden(new CatalogoComandos(),new FakeRepo(true,true)).procesar("e@gmail.com",new Orden("REGEXA",java.util.List.of("1","120","fuera de rango")));
        assertFalse(r.exito());assertTrue(r.cuerpo().contains("entre 0 y 100"));
    }
    private static final class FakeRepo implements RepositorioProyecto {
        private final boolean usuarios,autorizado;FakeRepo(boolean u,boolean a){usuarios=u;autorizado=a;}
        public boolean autorizado(String c,String r){return autorizado;}public boolean hayUsuarios(){return usuarios;}public java.util.Set<String> correosProcesados(){return java.util.Set.of();}
        public boolean correoProcesado(String id){return false;}public boolean servicioInicializado(){return true;}
        public void marcarServicioInicializado(){}public void registrarCorreoProcesado(String a,String b,String c,boolean d){}
        public Resultado ejecutar(String r,Orden o){return Resultado.ok("ejecutado");}
    }
}
