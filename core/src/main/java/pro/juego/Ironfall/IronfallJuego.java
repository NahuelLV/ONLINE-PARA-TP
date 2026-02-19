package pro.juego.Ironfall;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import pro.juego.Ironfall.server.GameStateServidor;

public class IronfallJuego extends ApplicationAdapter {

    private GameStateServidor gameState;

    @Override
    public void create() {
        gameState = new GameStateServidor();
    }

    @Override
    public void render() {

        float delta = Gdx.graphics.getDeltaTime();

        // Actualizamos el servidor
        gameState.update(delta);

        // Limpiamos pantalla
        Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }
}
