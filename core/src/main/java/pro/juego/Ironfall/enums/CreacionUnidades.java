package pro.juego.Ironfall.enums;

import pro.juego.Ironfall.entidades.ArqueroServidor;
import pro.juego.Ironfall.entidades.BestiaServidor;
import pro.juego.Ironfall.entidades.EspadachinServidor;
import pro.juego.Ironfall.entidades.UnidadServidor;


public class CreacionUnidades {

    public static UnidadServidor crearUnidad(
            TipoUnidad tipo,
            float x,
            float y,
            int equipo
    ) {

        switch (tipo) {

            case ESPADACHIN:
                return new EspadachinServidor(x, y, equipo);

            case BESTIA:
                return new BestiaServidor(x, y, equipo);

            case ARQUERO:
                return new ArqueroServidor(x, y, equipo);

            default:
                throw new RuntimeException(
                        "TipoUnidad no soportado: " + tipo
                );
        }
    }
}