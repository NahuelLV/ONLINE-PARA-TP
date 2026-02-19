package pro.juego.Ironfall.entidades;

public class EspadachinServidor extends UnidadServidor {

    public EspadachinServidor(float x, float y, int equipo) {
        super(x, y, equipo);

        this.vida = 100f;
        this.danio = 15f;
        this.velocidad = 60f;
        this.rangoAtaque = 30f;
        this.tiempoEntreAtaques = 1f;
    }
}
