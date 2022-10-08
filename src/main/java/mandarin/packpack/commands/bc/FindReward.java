package mandarin.packpack.commands.bc;

import common.CommonStatic;
import common.util.Data;
import common.util.lang.MultiLangCont;
import common.util.stage.MapColc;
import common.util.stage.Stage;
import common.util.stage.StageMap;
import mandarin.packpack.commands.ConstraintCommand;
import mandarin.packpack.commands.TimedConstraintCommand;
import mandarin.packpack.supporter.StaticStore;
import mandarin.packpack.supporter.bc.EntityFilter;
import mandarin.packpack.supporter.bc.EntityHandler;
import mandarin.packpack.supporter.lang.LangID;
import mandarin.packpack.supporter.server.data.ConfigHolder;
import mandarin.packpack.supporter.server.data.IDHolder;
import mandarin.packpack.supporter.server.holder.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;

import java.util.ArrayList;
import java.util.List;

public class FindReward extends TimedConstraintCommand {
    private static final int PARAM_EXTRA = 2;
    private static final int PARAM_COMPACT = 4;

    private final ConfigHolder config;

    public FindReward(ConstraintCommand.ROLE role, int lang, IDHolder idHolder, long time, ConfigHolder config) {
        super(role, lang, idHolder, time, StaticStore.COMMAND_FINDREWARD_ID);

        if(config == null)
            this.config = idHolder.config;
        else
            this.config = config;
    }

    @Override
    public void prepare() throws Exception {
        registerRequiredPermission(Permission.MESSAGE_EXT_EMOJI, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS);
    }

    @Override
    public void doSomething(GenericMessageEvent event) throws Exception {
        MessageChannel ch = getChannel(event);

        if(ch == null)
            return;

        String rewardName = getRewardName(getContent(event));

        int param = checkParameters(getContent(event));

        boolean isExtra = (param & PARAM_EXTRA) > 0 || config.extra;
        boolean isCompact = (param & PARAM_COMPACT) > 0 || (holder.forceCompact ? holder.config.compact : config.compact);

        double chance = getChance(getContent(event));
        int amount = getAmount(getContent(event));

        if(rewardName.isBlank()) {
            createMessageWithNoPings(ch, LangID.getStringByID("freward_noname", lang));

            return;
        }

        if(chance != -1 && (chance <= 0 || chance > 100)) {
            createMessageWithNoPings(ch, LangID.getStringByID("freward_chance", lang));

            return;
        }

        if(amount != -1 && amount <= 0) {
            createMessageWithNoPings(ch, LangID.getStringByID("freward_amount", lang));

            return;
        }

        List<Integer> rewards = EntityFilter.findRewardByName(rewardName, lang);

        if(rewards.isEmpty()) {
            createMessageWithNoPings(ch, LangID.getStringByID("freward_norew", lang).replace("_", rewardName));

            disableTimer();
        } else if(rewards.size() == 1) {
            List<Stage> stages = EntityFilter.findStageByReward(rewards.get(0), chance, amount);

            if(stages.isEmpty()) {
                createMessageWithNoPings(ch, LangID.getStringByID("freward_nosta", lang).replace("_", rewardName));

                disableTimer();
            } else if(stages.size() == 1) {
                Message result = EntityHandler.showStageEmb(stages.get(0), ch, holder.config.useFrame, isExtra, isCompact, 0, lang);

                Member m = getMember(event);

                if(m != null) {
                    Message msg = getMessage(event);

                    if(msg != null) {
                        StaticStore.putHolder(m.getId(), new StageInfoButtonHolder(stages.get(0), msg, result, ch.getId()));
                    }
                }
            } else {
                StringBuilder sb = new StringBuilder(LangID.getStringByID("freward_severalst", lang).replace("_", rewardName))
                        .append("```md\n")
                        .append(LangID.getStringByID("formst_pick", lang));

                List<String> data = accumulateStage(stages, true);

                for(int i = 0; i < data.size(); i++) {
                    sb.append(i+1).append(". ").append(data.get(i)).append("\n");
                }

                if(stages.size() > SearchHolder.PAGE_CHUNK) {
                    int totalPage = stages.size() / SearchHolder.PAGE_CHUNK;

                    if(stages.size() % SearchHolder.PAGE_CHUNK != 0)
                        totalPage++;

                    sb.append(LangID.getStringByID("formst_page", lang).replace("_", String.valueOf(1)).replace("-", String.valueOf(totalPage))).append("\n");
                }

                sb.append("```");

                Message res = registerSearchComponents(ch.sendMessage(sb.toString()).setAllowedMentions(new ArrayList<>()), stages.size(), accumulateStage(stages, false), lang).complete();

                if(res != null) {
                    Member member = getMember(event);

                    if(member != null) {
                        Message msg = getMessage(event);

                        if(msg != null)
                            StaticStore.putHolder(member.getId(), new StageInfoMessageHolder(stages, msg, res, ch.getId(), 0, config.useFrame, isExtra, isCompact, lang));
                    }
                }
            }
        } else {
            StringBuilder sb = new StringBuilder(LangID.getStringByID("freward_several", lang).replace("_", rewardName))
                    .append("```md\n")
                    .append(LangID.getStringByID("formst_pick", lang));

            List<String> data = accumulateReward(rewards);

            for(int i = 0; i < data.size(); i++) {
                sb.append(i+1).append(". ").append(data.get(i)).append("\n");
            }

            if(rewards.size() > SearchHolder.PAGE_CHUNK) {
                int totalPage = rewards.size() / SearchHolder.PAGE_CHUNK;

                if(rewards.size() % SearchHolder.PAGE_CHUNK != 0)
                    totalPage++;

                sb.append(LangID.getStringByID("formst_page", lang).replace("_", String.valueOf(1)).replace("-", String.valueOf(totalPage))).append("\n");
            }

            sb.append("```");

            Message res = registerSearchComponents(ch.sendMessage(sb.toString()).setAllowedMentions(new ArrayList<>()), rewards.size(), data, lang).complete();

            if(res != null) {
                Member member = getMember(event);

                if(member != null) {
                    Message msg = getMessage(event);

                    if(msg != null) {
                        StaticStore.putHolder(member.getId(), new FindRewardMessageHolder(res, msg, ch.getId(), rewards, rewardName, chance, amount, isExtra, isCompact, config.useFrame, lang));
                    }
                }
            }

            disableTimer();
        }
    }

    private String getRewardName(String message) {
        String[] contents = message.split(" ");

        if(contents.length < 2)
            return "";

        StringBuilder result = new StringBuilder();

        boolean extra = false;
        boolean compact = false;
        boolean chance = false;
        boolean amount = false;

        for(int i = 1; i < contents.length; i++) {
            if (!extra && (contents[i].equals("-e") || contents[i].equals("-extra"))) {
                extra = true;
            } else if (!compact && (contents[i].equals("-c") || contents[i].equals("-compact"))) {
                compact = true;
            } else if(!chance && (contents[i].equals("-ch") || contents[i].equals("-chance")) && i < contents.length - 1 && StaticStore.isNumeric(contents[i + 1])) {
                chance = true;
                i++;
            } else if(!amount && (contents[i].equals("-a") || contents[i].equals("-amount")) && i < contents.length - 1 && StaticStore.isNumeric(contents[i + 1])) {
                amount = true;
                i++;
            } else {
                result.append(contents[i]);

                if(i < contents.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString().trim();
    }

    private int checkParameters(String message) {
        String[] msg = message.split(" ");

        int result = 1;

        if(msg.length >= 2) {
            String[] pureMessage = message.split(" ", 2)[1].split(" ");

            label:
            for(String str : pureMessage) {
                switch (str) {
                    case "-e":
                    case "-extra":
                        if ((result & PARAM_EXTRA) == 0) {
                            result |= PARAM_EXTRA;
                        } else
                            break label;
                        break;
                    case "-c":
                    case "-compact":
                        if ((result & PARAM_COMPACT) == 0) {
                            result |= PARAM_COMPACT;
                        } else
                            break label;
                        break;
                }
            }
        }

        return result;
    }

    private double getChance(String content) {
        String[] contents = content.split(" ");

        for(int i = 0; i < contents.length; i++) {
            if((contents[i].equals("-ch") || contents[i].equals("-chance")) && i < contents.length - 1 && StaticStore.isNumeric(contents[i + 1])) {
                double value = Double.parseDouble(contents[i + 1]);

                if(value < 0)
                    return -100;
                else
                    return value;
            }
        }

        return -1;
    }

    private int getAmount(String content) {
        String[] contents = content.split(" ");

        for(int i = 0; i < contents.length; i++) {
            if((contents[i].equals("-a") || contents[i].equals("-amount")) && i < contents.length - 1 && StaticStore.isNumeric(contents[i + 1])) {
                int value = StaticStore.safeParseInt(contents[i + 1]);

                if(value < 0)
                    return -100;
                else
                    return value;
            }
        }

        return -1;
    }

    private List<String> accumulateReward(List<Integer> rewards) {
        List<String> data = new ArrayList<>();

        for(int i = 0; i < SearchHolder.PAGE_CHUNK; i++) {
            if(i >= rewards.size())
                break;

            String rname = Data.trio(rewards.get(i)) + " ";

            int oldConfig = CommonStatic.getConfig().lang;
            CommonStatic.getConfig().lang = lang;

            String name = MultiLangCont.getStatic().RWNAME.getCont(rewards.get(i));

            CommonStatic.getConfig().lang = oldConfig;

            if(name != null && !name.isBlank()) {
                rname += name;
            }

            data.add(rname);
        }

        return data;
    }

    private List<String> accumulateStage(List<Stage> stage, boolean onText) {
        List<String> data = new ArrayList<>();

        for(int i = 0; i < SearchHolder.PAGE_CHUNK; i++) {
            if(i >= stage.size())
                break;

            Stage st = stage.get(i);
            StageMap stm = st.getCont();
            MapColc mc = stm.getCont();

            String name = "";

            if(onText) {
                if(mc != null) {
                    int oldConfig = CommonStatic.getConfig().lang;
                    CommonStatic.getConfig().lang = lang;

                    String mcn = MultiLangCont.get(mc);

                    CommonStatic.getConfig().lang = oldConfig;

                    if(mcn == null || mcn.isBlank())
                        mcn = mc.getSID();

                    name += mcn+" - ";
                } else {
                    name += "Unknown - ";
                }
            }

            int oldConfig = CommonStatic.getConfig().lang;
            CommonStatic.getConfig().lang = lang;

            String stmn = MultiLangCont.get(stm);

            CommonStatic.getConfig().lang = oldConfig;

            if(stm.id != null) {
                if(stmn == null || stmn.isBlank())
                    stmn = Data.trio(stm.id.id);
            } else {
                if(stmn == null || stmn.isBlank())
                    stmn = "Unknown";
            }

            name += stmn+" - ";

            CommonStatic.getConfig().lang = lang;

            String stn = MultiLangCont.get(st);

            CommonStatic.getConfig().lang = oldConfig;

            if(st.id != null) {
                if(stn == null || stn.isBlank())
                    stn = Data.trio(st.id.id);
            } else {
                if(stn == null || stn.isBlank())
                    stn = "Unknown";
            }

            name += stn;

            data.add(name);
        }

        return data;
    }
}