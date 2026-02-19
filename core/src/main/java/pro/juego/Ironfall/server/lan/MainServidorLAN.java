package pro.juego.Ironfall.server.lan;

public class MainServidorLAN {

    public static void main(String[] args) {

        System.out.println("[SERVER] Ironfall LAN escuchando en puerto " + ServidorOnline.PORT);
        System.out.println("[SERVER] Servidor Ironfall LAN iniciado...");

        ServidorOnline server = new ServidorOnline();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::cerrar));
    }
}
