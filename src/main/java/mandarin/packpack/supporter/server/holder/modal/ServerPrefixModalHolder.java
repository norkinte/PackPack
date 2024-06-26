package mandarin.packpack.supporter.server.holder.modal;

import mandarin.packpack.supporter.lang.LangID;
import mandarin.packpack.supporter.server.data.ConfigHolder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class ServerPrefixModalHolder extends ModalHolder {
    private final ConfigHolder config;
    private final int lang;

    public ServerPrefixModalHolder(@NotNull Message author, @NotNull String channelID, @NotNull Message message, ConfigHolder config, int lang) {
        super(author, channelID, message);

        this.config = config;
        this.lang = lang;
    }

    @Override
    public void onEvent(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().equals("prefix"))
            return;

        String value = getValueFromMap(event.getValues(), "prefix");

        if (value.trim().isBlank()) {
            event.deferReply()
                    .setContent(LangID.getStringByID("sercon_empty", lang))
                    .setEphemeral(true)
                    .queue();

            return;
        }

        if (value.matches("(.+)?http(s)?://(.+)?")) {
            event.deferReply()
                    .setContent(LangID.getStringByID("sercon_nourl", lang))
                    .setEphemeral(true)
                    .queue();

            return;
        }

        config.prefix = value.replaceAll("\\s", "");

        event.deferReply()
                .setContent(LangID.getStringByID("sercon_prefixsuc", lang).formatted(value))
                .setEphemeral(true)
                .queue();

        goBack();
    }

    @Override
    public void clean() {

    }

    @Override
    public void onExpire(String id) {

    }
}
