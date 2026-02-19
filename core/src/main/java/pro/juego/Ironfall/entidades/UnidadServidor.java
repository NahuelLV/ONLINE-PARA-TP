package pro.juego.Ironfall.entidades;

import pro.juego.Ironfall.enums.EstadoUnidad;

import java.util.List;

public abstract class UnidadServidor {

    protected float x;
    protected float y;

    protected float vida;
    protected float danio;
    protected float velocidad;
    protected float rangoAtaque;
    protected float tiempoEntreAtaques;
    protected float tiempoDesdeUltimoAtaque = 0f;

    protected boolean viva = true;
    protected int equipo;
    protected int direccion;

    protected EstadoUnidad estado = EstadoUnidad.IDLE;

    public UnidadServidor(float x, float y, int equipo) {
        this.x = x;
        this.y = y;
        this.equipo = equipo;
        this.direccion = (equipo == 0) ? 1 : -1;
    }

    // =========================
    // UPDATE PRINCIPAL
    // =========================
    public void update(
            float delta,
            List<UnidadServidor> enemigos,
            EstatuaServidor estatuaEnemiga
    ) {

        if (!viva) {
            estado = EstadoUnidad.MUERTO;
            return;
        }

        tiempoDesdeUltimoAtaque += delta;

        UnidadServidor enemigo = buscarEnemigoCercano(enemigos);

        if (enemigo != null) {
            atacarUnidad(enemigo);
            return;
        }

        if (estatuaEnemiga != null && estatuaEnemiga.estaViva()) {
            float dist = Math.abs(estatuaEnemiga.getX() - x);
            if (dist <= rangoAtaque) {
                atacarEstatua(estatuaEnemiga);
                return;
            }
        }

        avanzar(delta);
    }

    protected void avanzar(float delta) {
        estado = EstadoUnidad.CAMINANDO;
        x += velocidad * delta * direccion;
    }

    protected void atacarUnidad(UnidadServidor objetivo) {
        if (tiempoDesdeUltimoAtaque < tiempoEntreAtaques) return;

        estado = EstadoUnidad.ATACANDO;
        objetivo.recibirDanio(danio);
        tiempoDesdeUltimoAtaque = 0f;
    }

    protected UnidadServidor buscarEnemigoCercano(List<UnidadServidor> enemigos) {
        for (UnidadServidor u : enemigos) {
            if (!u.estaViva()) continue;
            float dist = Math.abs(u.x - x);
            if (dist <= rangoAtaque)
                return u;
        }
        return null;
    }

    protected void atacarEstatua(EstatuaServidor estatua) {
        if (tiempoDesdeUltimoAtaque < tiempoEntreAtaques) return;

        estado = EstadoUnidad.ATACANDO;
        estatua.recibirDanio(danio);
        tiempoDesdeUltimoAtaque = 0f;
    }

    public void recibirDanio(float cantidad) {
        vida -= cantidad;

        if (vida <= 0) {
            vida = 0;
            viva = false;
            estado = EstadoUnidad.MUERTO;
        } else {
            estado = EstadoUnidad.RECIBIENDO_DAÑO;
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

    public float getVida() {
        return vida;
    }

    public int getEquipo() {
        return equipo;
    }

    public EstadoUnidad getEstado() {
        return estado;
    }
}
