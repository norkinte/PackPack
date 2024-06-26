package mandarin.card.supporter.holder.slot

import mandarin.card.supporter.CardData
import mandarin.card.supporter.slot.SlotCardContent
import mandarin.card.supporter.slot.SlotCurrencyContent
import mandarin.card.supporter.slot.SlotEntryFee
import mandarin.card.supporter.slot.SlotMachine
import mandarin.packpack.supporter.EmojiStore
import mandarin.packpack.supporter.lang.LangID
import mandarin.packpack.supporter.server.holder.component.ComponentHolder
import mandarin.packpack.supporter.server.holder.component.ConfirmPopUpHolder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu

class SlotMachineContentHolder(author: Message, channelID: String, private val message: Message, private val slotMachine: SlotMachine) : ComponentHolder(author, channelID, message) {
    override fun clean() {

    }

    override fun onExpire(id: String?) {

    }

    override fun onEvent(event: GenericComponentInteractionCreateEvent) {
        when(event.componentId) {
            "create" -> {
                connectTo(event, SlotMachineRewardTypeHolder(authorMessage, channelID, message, slotMachine))
            }
            "reward" -> {
                if (event !is StringSelectInteractionEvent)
                    return

                when(val content = slotMachine.content[event.values[0].toInt()]) {
                    is SlotCurrencyContent -> connectTo(event, SlotMachineCurrencyRewardHolder(authorMessage, channelID, message, slotMachine, content, false))
                    is SlotCardContent -> connectTo(event, SlotMachineCardRewardHolder(authorMessage, channelID, message, slotMachine, content, false))
                }
            }
            "back" -> {
                goBack(event)
            }
            "cancel" -> {
                registerPopUp(event, "Are you sure you want to cancel creation of slot machine? This cannot be undone", LangID.EN)

                connectTo(ConfirmPopUpHolder(authorMessage, channelID, message, { e ->
                    e.deferReply()
                        .setContent("Canceled creation of slot machine")
                        .setEphemeral(true)
                        .queue()

                    goBackTo(SlotMachineListHolder::class.java)
                }, LangID.EN))
            }
        }
    }

    override fun onBack() {
        applyResult()
    }

    override fun onBack(event: GenericComponentInteractionCreateEvent) {
        applyResult(event)
    }

    override fun onConnected(event: GenericComponentInteractionCreateEvent) {
        applyResult(event)
    }

    private fun applyResult() {
        message.editMessage(getContents())
            .setComponents(getComponents())
            .setAllowedMentions(ArrayList())
            .mentionRepliedUser(false)
            .queue()
    }

    private fun applyResult(event: GenericComponentInteractionCreateEvent) {
        event.deferEdit()
            .setContent(getContents())
            .setComponents(getComponents())
            .setAllowedMentions(ArrayList())
            .mentionRepliedUser(false)
            .queue()
    }

    private fun getContents() : String {
        val builder = StringBuilder()
            .append("# ")
            .append(slotMachine.name)
            .append("\n" +
                    "## Slot Machine Reward Section\n" +
                    "In this section, you can manage rewards upon full sequence of same specific emoji." +
                    " Select reward to modify or remove it." +
                    " Click `Create New Reward` button to create new reward." +
                    " Reward type depends on entry fee type\n" +
                    "## List of Reward\n")

        if (slotMachine.content.isEmpty()) {
            builder.append("- No Rewards")
        } else {
            val emoji = when(slotMachine.entryFee.entryType) {
                SlotEntryFee.EntryType.CAT_FOOD -> EmojiStore.ABILITY["CF"]?.formatted
                SlotEntryFee.EntryType.PLATINUM_SHARDS -> EmojiStore.ABILITY["SHARD"]?.formatted
            }

            slotMachine.content.forEachIndexed { index, content ->
                builder.append(index + 1).append(". ").append(content.emoji?.formatted ?: EmojiStore.UNKNOWN.formatted)

                when (content) {
                    is SlotCurrencyContent -> {
                        when(content.mode) {
                            SlotCurrencyContent.Mode.FLAT -> {

                                builder.append(" [Flat] : ")
                                    .append(emoji)
                                    .append(" ")
                                    .append(content.amount)
                            }
                            SlotCurrencyContent.Mode.PERCENTAGE -> {
                                builder.append(" [Percentage] : ")
                                    .append(content.amount)
                                    .append("% of Entry Fee")
                            }
                        }
                    }
                    is SlotCardContent -> {
                        builder.append(" [Card] : ").append(content.name).append("\n")

                        content.cardChancePairLists.forEachIndexed { ind, l ->
                            builder.append("  - ").append(l.amount).append(" ")

                            if (l.amount >= 2) {
                                builder.append("Cards\n")
                            } else {
                                builder.append("Card\n")
                            }

                            l.pairs.forEachIndexed { i, p ->
                                builder.append("    - ").append(CardData.df.format(p.chance)).append("% : ").append(p.cardGroup.getName())

                                if (i < l.pairs.size - 1)
                                    builder.append("\n")
                            }

                            if (ind < content.cardChancePairLists.size - 1)
                                builder.append("\n")
                        }
                    }
                }

                if (index < slotMachine.content.size - 1)
                    builder.append("\n")
            }
        }

        return builder.toString()
    }

    private fun getComponents() : List<LayoutComponent> {
        val result = ArrayList<LayoutComponent>()

        val options = ArrayList<SelectOption>()

        if (slotMachine.content.isEmpty()) {
            options.add(SelectOption.of("A", "A"))
        } else {
            slotMachine.content.forEachIndexed { i, content ->
                val description = when (content) {
                    is SlotCurrencyContent -> {
                        when (content.mode) {
                            SlotCurrencyContent.Mode.FLAT -> "[Flat] : ${content.amount}"
                            SlotCurrencyContent.Mode.PERCENTAGE -> "[Percentage] : ${content.amount}% of Entry Fee"
                        }
                    }
                    is SlotCardContent -> {
                        "[Card]"
                    }
                    else -> {
                        throw IllegalStateException("E/SlotMachineContentHolder::getComponents - Unknown slot machine content type : ${content.javaClass.name}")
                    }
                }

                options.add(SelectOption.of("${i + 1}. ${content.emoji?.name ?: "UNKNOWN"}", i.toString()).withEmoji(content.emoji).withDescription(description))
            }
        }

        result.add(ActionRow.of(
            StringSelectMenu.create("reward")
                .addOptions(options)
                .setPlaceholder("Select Reward To Modify")
                .setDisabled(slotMachine.content.isEmpty())
                .build()
        ))

        val emojiList = arrayOf("🍌", "🍇", "🥝", "🍊", "🍎")

        result.add(ActionRow.of(Button.secondary("create", "Create New Reward").withEmoji(Emoji.fromUnicode(emojiList.random())).withDisabled(slotMachine.content.size >= StringSelectMenu.OPTIONS_MAX_AMOUNT)))

        if (slotMachine !in CardData.slotMachines) {
            result.add(ActionRow.of(
                Button.secondary("back", "Back").withEmoji(EmojiStore.BACK),
                Button.danger("cancel", "Cancel").withEmoji(EmojiStore.CROSS)
            ))
        } else {
            result.add(ActionRow.of(
                Button.secondary("back", "Back").withEmoji(EmojiStore.BACK)
            ))
        }

        return result
    }
}