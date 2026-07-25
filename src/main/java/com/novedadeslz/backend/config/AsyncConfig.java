package com.novedadeslz.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pool acotado para trabajo diferido, hoy solo notificaciones de WhatsApp.
 *
 * <p>Los limites son deliberadamente bajos: el contenedor de Render tiene poca memoria y estas
 * tareas solo esperan por una API externa. Si el proveedor se cae, preferimos descartar
 * notificaciones (el admin las puede reenviar desde el panel) antes que acumular tareas hasta
 * quedarnos sin memoria.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    public static final String NOTIFICATIONS_EXECUTOR = "notificationsExecutor";

    @Bean(name = NOTIFICATIONS_EXECUTOR)
    public Executor notificationsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        // Descartar y registrar, nunca CallerRunsPolicy: eso devolveria el envio al hilo del
        // request, que es justo lo que este refactor busca evitar.
        executor.setRejectedExecutionHandler((runnable, poolExecutor) ->
                log.error("Cola de notificaciones llena; se descarta el envio. "
                        + "El admin puede reenviarlo desde el panel de pedidos.")
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
