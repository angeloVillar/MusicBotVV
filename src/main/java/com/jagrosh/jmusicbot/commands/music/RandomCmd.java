package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.DJCommand;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.PermissionException;

import java.util.concurrent.TimeUnit;

import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RandomCmd extends MusicCommand {
    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    private final String loadingEmoji;

    public RandomCmd(Bot bot)
    {
        super(bot);
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "random";
        this.arguments = "<title|URL|subcommand>";
        this.help = "plays the provided playlist randomly";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
        this.children = new Command[]{new RandomCmd.PlaylistCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        try {
            // Spotify Workaround
            final Matcher spotifyMatcher = SpotifySourceManager.URL_PATTERN.matcher(event.getArgs());
            if (spotifyMatcher.find()) {
                final Field argField = event.getClass().getDeclaredField("args");
                argField.setAccessible(true);
                final String arg = (String) argField.get(event);
                final String region = spotifyMatcher.group("region");
                if (region == null || region.isEmpty()) {
                    argField.set(event, arg.replace("spotify.com/", "spotify.com/intl/"));
                } else if (!region.equals("intl")) argField.set(event, arg.replace(region, "intl"));
            }

        } catch (Exception ignored) {
        }
        String argsIn = event.getArgs();
        if(argsIn.isEmpty() && event.getMessage().getAttachments().isEmpty())
        {
            AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
            if(handler.getPlayer().getPlayingTrack()!=null && handler.getPlayer().isPaused())
            {
                if(DJCommand.checkDJPermission(event))
                {
                    handler.getPlayer().setPaused(false);
                    event.replySuccess("Resumed **"+handler.getPlayer().getPlayingTrack().getInfo().title+"**.");
                }
                else
                    event.replyError("Only DJs can unpause the player!");
                return;
            }
            StringBuilder builder = new StringBuilder(event.getClient().getWarning()+" Play Commands:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <song title>` - plays the first result from Youtube");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - plays the provided song, playlist, or stream");
            for(Command cmd: children)
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            event.reply(builder.toString());
            return;
        }
        String args = event.getArgs().startsWith("<") && event.getArgs().endsWith(">")
                ? event.getArgs().substring(1,event.getArgs().length()-1)
                : event.getArgs().isEmpty() ? event.getMessage().getAttachments().get(0).getUrl() : event.getArgs();
        event.reply(loadingEmoji+" Loading... `["+args+"]`", m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), args, new RandomCmd.ResultHandler(m,event,false)));
    }

    private class ResultHandler implements AudioLoadResultHandler
    {
        private final Message m;
        private final CommandEvent event;
        private final boolean ytsearch;

        private ResultHandler(Message m, CommandEvent event, boolean ytsearch)
        {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist)
        {
            if(bot.getConfig().isTooLong(track))
            {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" This track (**"+track.getInfo().title+"**) is longer than the allowed maximum: `"
                        + TimeUtil.formatTime(track.getDuration())+"` > `"+TimeUtil.formatTime(bot.getConfig().getMaxSeconds()*1000)+"`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
            RequestMetadata rm = new RequestMetadata(event.getAuthor(), new RequestMetadata.RequestInfo(event.getArgs(), track.getInfo().uri));
            int pos = handler.addTrack(new QueuedTrack(track, rm))+1;
            String addMsg = FormatUtil.filter(event.getClient().getSuccess()+" Added **"+track.getInfo().title
                    +"** (`"+TimeUtil.formatTime(track.getDuration())+"`) "+(pos==0?"to begin playing":" to the queue at position "+pos));
            if(playlist==null || !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION))
                m.editMessage(addMsg).queue();
            else
            {
                new ButtonMenu.Builder()
                        .setText(addMsg+"\n"+event.getClient().getWarning()+" This track has a playlist of **"+playlist.getTracks().size()+"** tracks attached. Select "+LOAD+" to load playlist.")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re ->
                        {
                            if(re.getName().equals(LOAD))
                                m.editMessage(addMsg+"\n"+event.getClient().getSuccess()+" Loaded **"+loadPlaylist(playlist, track)+"** additional tracks!").queue();
                            else
                                m.editMessage(addMsg).queue();
                        }).setFinalAction(m ->
                        {
                            try{ m.clearReactions().queue(); }catch(PermissionException ignore) {}
                        }).build().display(m);
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude)
        {
            int[] count = {0};
            playlist.getTracks().stream().forEach((track) -> {
                if(!bot.getConfig().isTooLong(track) && !track.equals(exclude))
                {
                    AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
                    RequestMetadata rm = new RequestMetadata(event.getAuthor(), new RequestMetadata.RequestInfo(event.getArgs(), track.getInfo().uri));
                    handler.addTrack(new QueuedTrack(track, rm));

                    if(count[0]==0){
                        handler.addTrack(new QueuedTrack(track, rm));
                    }

                    count[0]++;
                }
            });
            AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
            int s = handler.getQueue().shuffle(event.getAuthor().getIdLong());
            handler.getPlayer().stopTrack();
            switch (s)
            {
                case 0:
                    event.replyError("You don't have any music in the queue to shuffle!");
                    break;
                case 1:
                    event.replyWarning("You only have one song in the queue!");
                    break;
                default:
                    event.replySuccess("You successfully shuffled your "+s+" entries.");
                    break;
            }
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track)
        {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            if(playlist.getTracks().size()==1 || playlist.isSearchResult())
            {
                AudioTrack single = playlist.getSelectedTrack()==null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            }
            else if (playlist.getSelectedTrack()!=null)
            {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            }
            else
            {
                int count = loadPlaylist(playlist, null);
                if(playlist.getTracks().size() == 0)
                {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" The playlist "+(playlist.getName()==null ? "" : "(**"+playlist.getName()
                            +"**) ")+" could not be loaded or contained 0 entries")).queue();
                }
                else if(count==0)
                {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" All entries in this playlist "+(playlist.getName()==null ? "" : "(**"+playlist.getName()
                            +"**) ")+"were longer than the allowed maximum (`"+bot.getConfig().getMaxTime()+"`)")).queue();
                }
                else
                {
                    m.editMessage(FormatUtil.filter(event.getClient().getSuccess()+" Found "
                            +(playlist.getName()==null?"a playlist":"playlist **"+playlist.getName()+"**")+" with `"
                            + playlist.getTracks().size()+"` entries; added to the queue!"
                            + (count<playlist.getTracks().size() ? "\n"+event.getClient().getWarning()+" Tracks longer than the allowed maximum (`"
                            + bot.getConfig().getMaxTime()+"`) have been omitted." : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches()
        {
            if(ytsearch)
                m.editMessage(FormatUtil.filter(event.getClient().getWarning()+" No results found for `"+event.getArgs()+"`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:"+event.getArgs(), new RandomCmd.ResultHandler(m,event,true));
        }

        @Override
        public void loadFailed(FriendlyException throwable)
        {
            if(throwable.severity== FriendlyException.Severity.COMMON)
                m.editMessage(event.getClient().getError()+" Error loading: "+throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError()+" Error loading track.").queue();
        }
    }

    public class PlaylistCmd extends MusicCommand
    {
        public PlaylistCmd(Bot bot)
        {
            super(bot);
            this.name = "playlist";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "plays the provided playlist";
            this.beListening = true;
            this.bePlaying = false;
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            if(event.getArgs().isEmpty())
            {
                event.reply(event.getClient().getError()+" Please include a playlist name.");
                return;
            }
            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getArgs());

            for (int i = 0; i < playlist.getItems().size(); i++) {
                String item = playlist.getItems().get(i);
                String modifiedItem = item; // Start with the original item

                // Apply Spotify workaround
                final Matcher spotifyMatcher = SpotifySourceManager.URL_PATTERN.matcher(item);
                if (spotifyMatcher.find()) {
                    String region = spotifyMatcher.group("region");
                    if (region == null || region.isEmpty()) {
                        modifiedItem = item.replace("spotify.com/", "spotify.com/intl/");
                    } else if (!region.equals("intl")) {
                        modifiedItem = item.replace(region, "intl");
                    }
                }

                // Update the item in the existing playlist
                playlist.getItems().set(i, modifiedItem);
            }

            if(playlist==null)
            {
                event.replyError("I could not find `"+event.getArgs()+".txt` in the Playlists folder.");
                return;
            }
            int[] count = {0};
            event.getChannel().sendMessage(loadingEmoji+" Loading playlist **"+event.getArgs()+"**... ("+playlist.getItems().size()+" items)").queue(m ->
            {
                AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> {
                            RequestMetadata rm = new RequestMetadata(event.getAuthor(), new RequestMetadata.RequestInfo(event.getArgs(), at.getInfo().uri));
                            handler.addTrack(new QueuedTrack(at, rm));
                            if(count[0]==0){
                                handler.getPlayer().setPaused(true);
                                handler.addTrack(new QueuedTrack(at, rm));
                            }
                            count[0]++;
                        }
                        , () -> {
                            StringBuilder builder = new StringBuilder();

                            if (playlist.getTracks().isEmpty()) {
                                builder.append(event.getClient().getWarning()).append(" No tracks were loaded!");
                            } else {

                                //fork
                                shuffle(event);
                                handler.getPlayer().setPaused(false);

                                builder.append(event.getClient().getSuccess())
                                        .append(" Loaded **")
                                        .append(playlist.getTracks().size())
                                        .append("** tracks!");
                            }

                            if(!playlist.getErrors().isEmpty())
                                builder.append("\nThe following tracks failed to load:");
                            playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex()+1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                            String str = builder.toString();
                            if(str.length()>2000)
                                str = str.substring(0,1994)+" (...)";
                            m.editMessage(FormatUtil.filter(str)).queue();
                        });
            });
        }
    }

    //fork
    public void shuffle(CommandEvent event){
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        int s = handler.getQueue().shuffle(event.getAuthor().getIdLong());
        handler.getPlayer().stopTrack();
        switch (s)
        {
            case 0:
                event.replyError("You don't have any music in the queue to shuffle!");
                break;
            case 1:
                event.replyWarning("You only have one song in the queue!");
                break;
            default:
                event.replySuccess("You successfully shuffled your "+s+" entries.");
                break;
        }
    }

}
