package pro.juego.Ironfall.entidades;

public class BestiaServidor extends UnidadServidor {

    public BestiaServidor(float x, float y, int equipo) {
        super(x, y, equipo);

        this.vida = 220f;
        this.danio = 35f;
        this.velocidad = 45f;
        this.rangoAtaque = 26f;
        this.tiempoEntreAtaques = 1.4f;
    }
}
