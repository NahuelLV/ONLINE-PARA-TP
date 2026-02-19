package pro.juego.Ironfall.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import pro.juego.Ironfall.entidades.EstatuaServidor;
import pro.juego.Ironfall.entidades.UnidadServidor;

public class GameStateServidor {

    private List<UnidadServidor> unidadesEquipo0 = new ArrayList<>();
    private List<UnidadServidor> unidadesEquipo1 = new ArrayList<>();

    private EstatuaServidor estatua0;
    private EstatuaServidor estatua1;

    private boolean juegoTerminado = false;
    private int ganador = -1;

    public GameStateServidor() {

        estatua0 = new EstatuaServidor(100, 200, 0);
        estatua1 = new EstatuaServidor(900, 200, 1);
    }

    public void update(float delta) {

        if (juegoTerminado) return;

        // Actualizar estatuas
        estatua0.update(delta, unidadesEquipo1);
        estatua1.update(delta, unidadesEquipo0);

        // Producción
        UnidadServidor nueva0 = estatua0.updateProduccion(delta);
        if (nueva0 != null) unidadesEquipo0.add(nueva0);

        UnidadServidor nueva1 = estatua1.updateProduccion(delta);
        if (nueva1 != null) unidadesEquipo1.add(nueva1);

        // Actualizar unidades equipo 0
        for (UnidadServidor u : unidadesEquipo0) {
            u.update(delta, unidadesEquipo1, estatua1);
        }

        // Actualizar unidades equipo 1
        for (UnidadServidor u : unidadesEquipo1) {
            u.update(delta, unidadesEquipo0, estatua0);
        }

        limpiarMuertos(unidadesEquipo0);
        limpiarMuertos(unidadesEquipo1);

        verificarVictoria();
    }

    private void limpiarMuertos(List<UnidadServidor> lista) {
        Iterator<UnidadServidor> it = lista.iterator();
        while (it.hasNext()) {
            if (!it.next().estaViva()) {
                it.remove();
            }
        }
    }

    private void verificarVictoria() {
        if (!estatua0.estaViva()) {
            juegoTerminado = true;
            ganador = 1;
        }

        if (!estatua1.estaViva()) {
            juegoTerminado = true;
            ganador = 0;
        }
    }

    public boolean estaTerminado() {
        return juegoTerminado;
    }

    public int getGanador() {
        return ganador;
    }

    public EstatuaServidor getEstatua0() {
        return estatua0;
    }

    public EstatuaServidor getEstatua1() {
        return estatua1;
    }

    public List<UnidadServidor> getUnidadesEquipo0() {
        return unidadesEquipo0;
    }

    public List<UnidadServidor> getUnidadesEquipo1() {
        return unidadesEquipo1;
    }
}
