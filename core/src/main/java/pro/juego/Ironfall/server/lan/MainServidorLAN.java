package pro.juego.Ironfall.server.lan;

public class MainServidorLAN {

    public static void main(String[] args) {

        ServidorIronfallLAN server = new ServidorIronfallLAN();
        server.start();

        System.out.println("Servidor Ironfall LAN iniciado...");
    }
}
