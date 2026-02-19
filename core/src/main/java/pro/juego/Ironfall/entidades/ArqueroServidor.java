package pro.juego.Ironfall.entidades;

public class ArqueroServidor extends UnidadServidor {

        public ArqueroServidor(float x, float y, int equipo) {
                super(x, y, equipo);

                this.vida = 70f;
                this.danio = 18f;
                this.velocidad = 60f;
                this.rangoAtaque = 160f;
                this.tiempoEntreAtaques = 1.1f;
        }
}
