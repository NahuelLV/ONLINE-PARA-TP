package pro.juego.Ironfall.entidades;

import java.util.LinkedList;
import java.util.List;

import pro.juego.Ironfall.enums.TipoUnidad;
import pro.juego.Ironfall.enums.CreacionUnidades;

public class EstatuaServidor {

    // =========================
    // POSICIÓN
    // =========================
    private float x;
    private float y;

    // =========================
    // COMBATE
    // =========================
    private float vidaMax = 500f;
    private float vida = vidaMax;
    private boolean viva = true;

    private float danio = 30f;
    private float rangoAtaque = 250f;
    private float tiempoEntreAtaques = 1.2f;
    private float tiempoDesdeUltimoAtaque = 0f;

    private int equipo;

    // =========================
    // ORO
    // =========================
    private int oro = 0;
    private float timerOro = 0f;
    private static final float INTERVALO_ORO = 30f;
    private static final int ORO_POR_TICK = 500;

    // =========================
    // PRODUCCIÓN
    // =========================
    private List<TipoUnidad> colaProduccion = new LinkedList<>();
    private TipoUnidad produciendo = null;
    private float progresoProduccion = 0f;

    // =========================
    // CONSTRUCTOR
    // =========================
    public EstatuaServidor(float x, float y, int equipo) {
        this.x = x;
        this.y = y;
        this.equipo = equipo;
    }

    // =========================
    // UPDATE GENERAL
    // =========================
    public void update(float delta, List<UnidadServidor> enemigos) {
        if (!viva) return;

        generarOro(delta);

        tiempoDesdeUltimoAtaque += delta;
        atacarEnemigos(enemigos);
    }

    // =========================
    // ATAQUE
    // =========================
    private void atacarEnemigos(List<UnidadServidor> enemigos) {
        if (tiempoDesdeUltimoAtaque < tiempoEntreAtaques) return;

        for (UnidadServidor u : enemigos) {
            if (!u.estaViva()) continue;

            float distancia = Math.abs(u.getX() - x);
            if (distancia <= rangoAtaque) {
                u.recibirDanio(danio);
                tiempoDesdeUltimoAtaque = 0f;
                return;
            }
        }
    }

    // =========================
    // ORO
    // =========================
    private void generarOro(float delta) {
        timerOro += delta;
        if (timerOro >= INTERVALO_ORO) {
            oro += ORO_POR_TICK;
            timerOro = 0f;
        }
    }

    public boolean intentarProducir(TipoUnidad tipo) {
        if (oro < tipo.getCosto()) return false;

        oro -= tipo.getCosto();
        colaProduccion.add(tipo);
        return true;
    }

    // =========================
    // PRODUCCIÓN
    // =========================
    public UnidadServidor updateProduccion(float delta) {

        if (produciendo == null) {

            if (colaProduccion.isEmpty()) {
                return null;
            }

            produciendo = colaProduccion.remove(0);
            progresoProduccion = 0f;
        }

        progresoProduccion += delta;

        if (progresoProduccion < produciendo.getTiempoProduccion()) {
            return null;
        }

        float spawnX = (equipo == 0)
                ? x + 80f
                : x - 80f;

        float spawnY = y;

        UnidadServidor nueva = CreacionUnidades.crearUnidad(
                produciendo,
                spawnX,
                spawnY,
                equipo
        );

        produciendo = null;
        progresoProduccion = 0f;

        return nueva;
    }

    // =========================
    // DAÑO
    // =========================
    public void recibirDanio(float danio) {
        vida -= danio;

        if (vida <= 0) {
            vida = 0;
            viva = false;
        }
    }

    // =========================
    // GETTERS
    // =========================
    public boolean estaViva() {
        return viva;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getOro() {
        return oro;
    }

    public float getVida() {
        return vida;
    }

    public float getVidaMaxima() {
        return vidaMax;
    }

    public int getEquipo() {
        return equipo;
    }
}
