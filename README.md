# MusicBotVV
Un fork de jagrosh bot para el curso de verificación y validación

## Clase a analizar: PlayCmd.java

### Indicencia #1
Violación: Fragmentos de código similares
Corrección 1: Mover las instrucciones de 'Spotify workaround' a una función separada
Fragmento de código:
    public static String applySpotifyWorkaround(String input) {
        final Matcher spotifyMatcher = SpotifySourceManager.URL_PATTERN.matcher(input);
        if (spotifyMatcher.find()) {
            String region = spotifyMatcher.group("region");
            if (region == null || region.isEmpty()) {
                return input.replace("spotify.com/", "spotify.com/intl/");
            } else if (!region.equals("intl")) {
                return input.replace(region, "intl");
            }
        }
        return input;
    }
Corrección 2: Implementar la función en las 2 incidencias
Fragmento de código:
            // Spotify Workaround
            final Field argField = event.getClass().getDeclaredField("args");
            argField.setAccessible(true);
            final String arg = (String) argField.get(event);
            argField.set(event, applySpotifyWorkaround(arg));

### Incidencia #2
Violación: Imports de dependencias que no han sido usadas
Corrección: Eliminar dependencias inútiles
Fragmento de código: import java.util.regex.Pattern; import java.io.IOException;

### Incidencia #3
Violación: Paréntesis redundante en función lambda
Corrección: Remover el paréntesis
Fragmento de código: playlist.loadTracks(bot.getPlayerManager(), at->handler.addTrack(new QueuedTrack(at, RequestMetadata.fromResultHandler(at, event))), () -> { ...
					 playlist.getTracks().stream().forEach(track -> { ...

### Indicendia #4
Violación: Variable con mismo nombre, afecta a la mantenibilidad y puede ser ambigua para quien lee el código
Corrección: Refactorizar la variable
Fragmento de código:
                        }).setFinalAction(message -> {
                            try{ message.clearReactions().queue(); }catch(PermissionException ignore) {}
                        }).build().display(m);
						
### Incidencia #5
Violación: catch sin instrucciones
Corrección: Usar logger para registrar la excepción
Fragmento de código: catch (Exception ignored) {LOGGER.severe("Failed to apply Spotify workaround");}
					 catch(PermissionException ignore) {LOGGER.severe("PermissionException clearing reactions in PlayCmd.ResultHandler.loadSingle");}

### Incidencia #6
Violación: Orden de modificadores no cumplen con los estándares de programación en java
Corrección: Cambiar el órden de los modificadores para cumplir con Java Language Specification
Fragmento de código:     private static final String LOAD = "\uD83D\uDCE5";
						 private static final String CANCEL = "\uD83D\uDEAB";
						 
### Incidencia #7
Violación: Condicional verifica .size == 0, que tiene una complejidad de O(n)
Corrección: Cambiar por isEmpty() de complejidad O(1)
Fragmento de código: if(playlist.getTracks().isEmpty())

### Incidencia #8
Violación: Nesting en operador condicional
Corrección: Mover el código anidado a una declaración independiente
Fragmento de código:
	String args;
    if (event.getArgs().startsWith("<") && event.getArgs().endsWith(">")) {
        args = event.getArgs().substring(1, event.getArgs().length() - 1);
    } else if (event.getArgs().isEmpty()) {
        args = event.getMessage().getAttachments().get(0).getUrl();
    } else {
        args = event.getArgs();
    }
	
### Incidencia #9
Violación: Código potencialmente inalcanzable
Corrección: Eliminar declaración
Fragmento de código: if(playlist==null){return;}

### Indicencia #10
Violación: La complejidad del método playlistLoaded() puede reducirse de 22 a 15 puntos
Corrección: Separar playlistLoaded() en 6 funciones independientes: handleSingleTrackPlaylist, handleMultipleTracksPlaylist, handleEmptyPlaylist, handleAllTracksTooLong y handleSuccessfullyLoadedPlaylist
Fragmento de código:
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                handleSingleTrackPlaylist(playlist);
            } else if (playlist.getSelectedTrack() != null) {
                loadSingle(playlist.getSelectedTrack(), playlist);
            } else {
                handleMultipleTracksPlaylist(playlist);
            }
        }

