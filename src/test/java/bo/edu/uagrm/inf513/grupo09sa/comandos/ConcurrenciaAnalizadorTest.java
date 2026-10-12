package bo.edu.uagrm.inf513.grupo09sa.comandos;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrenciaAnalizadorTest {
    @Test void soportaRafagaConcurrenteSinCompartirEstado()throws Exception{
        AnalizadorOrden parser=new AnalizadorOrden();int hilos=32,porHilo=10_000;
        var tareas=new ArrayList<Callable<Integer>>();
        for(int h=0;h<hilos;h++){final int semilla=h;tareas.add(()->{SplittableRandom r=new SplittableRandom(semilla);int procesados=0;for(int i=0;i<porHilo;i++){
            String s=switch(r.nextInt(5)){case 0->"AYUDA[]";case 1->"REGEXA[\"1\",\"80\",\"ok\"]";case 2->"X[\""+"A".repeat(r.nextInt(1,520))+"\"]";case 3->"COMANDO[sin-comillas]";default->" ".repeat(r.nextInt(0,20));};
            try{parser.analizar(s);}catch(OrdenInvalidaException expected){}procesados++;
        }return procesados;});}
        var pool=Executors.newFixedThreadPool(hilos);try{List<java.util.concurrent.Future<Integer>> resultados=pool.invokeAll(tareas);int total=0;for(var f:resultados)total+=f.get();assertEquals(hilos*porHilo,total);}finally{pool.shutdownNow();}
    }
}
