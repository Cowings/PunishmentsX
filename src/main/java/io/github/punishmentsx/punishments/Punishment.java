package io.github.punishmentsx.punishments;

import io.github.punishmentsx.Locale;
import io.github.punishmentsx.PunishmentsX;
import io.github.punishmentsx.profiles.Profile;
import io.github.punishmentsx.utils.Notifications;
import io.github.punishmentsx.utils.PlayerUtil;
import io.github.punishmentsx.utils.WebHook;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public @Data class Punishment {

    public enum Type {
        BAN, BLACKLIST, KICK, MUTE, WARN;

        public String pastMessage() {
            switch(this) {
                case BAN:
                    return "banned";
                case BLACKLIST:
                    return "blacklisted";
                case KICK:
                    return "kicked";
                case MUTE:
                    return "muted";
                case WARN:
                    return "warned";
                default:
                    return "null";
            }
        }
    }

    private final PunishmentsX plugin;
    private final UUID uuid;
    private UUID victim, issuer, pardoner;
    private String stack, issueReason, pardonReason;
    private Date issued, expires, pardoned;
    private Type type;
    private boolean silentIssue, silentPardon;

    public String expiry() {
        if (expires == null) {
            return "Never";
        } else {
            return expires.toString();
        }
    }

    public boolean isActive() {
        boolean b = true;

        if(expires != null) {
            if(expires.before(new Date())) {
                b = false;
            }
        }

        if(pardoned != null) {
            b = false;
        }

        return b;
    }

    @SuppressWarnings("unchecked")
    public void execute() {
        Player player = Bukkit.getPlayer(victim);

        String victimName = null, issuerName;

        if(isActive()) {
            if (issuer != null) {
                Player p = Bukkit.getPlayer(issuer);
                issuerName = p.getName();
            } else {
                issuerName = Locale.CONSOLE_NAME.format(plugin);
            }
        } else {
            if (pardoner != null) {
                Player p = Bukkit.getPlayer(pardoner);
                issuerName = p.getName();
            } else {
                issuerName = Locale.CONSOLE_NAME.format(plugin);
            }
        }

        if(player != null && player.isOnline()) {
            victimName = player.getName();
            List<String> list = new ArrayList<>();
            if(isActive()) {
                switch (type) {
                    case BAN:
                        for (String string : Locale.BAN_MESSAGE.formatLines(plugin)) {
                            list.add(string
                                    .replace("%expirationDate%", expiry())
                                    .replace("%reason%", issueReason));
                        }
                        player.kickPlayer(String.join("\n", list));
                        break;
                    case BLACKLIST:
                        for (String string : Locale.BLACKLIST_MESSAGE.formatLines(plugin)) {
                            list.add(string.replace("%reason%", issueReason));
                        }
                        player.kickPlayer(String.join("\n", list));
                        break;
                    case MUTE:
                        for (String string : Locale.MUTE_MESSAGE.formatLines(plugin)) {
                            list.add(string
                                    .replace("%expirationDate%", expiry())
                                    .replace("%reason%", issueReason));
                        }
                        player.sendMessage(String.join("\n", list));
                        break;
                    case KICK:
                        for (String string : Locale.KICK_MESSAGE.formatLines(plugin)) {
                            list.add(string.replace("%reason%", issueReason));
                        }
                        player.kickPlayer(String.join("\n", list));
                        break;
                    case WARN:
                        for (String string : Locale.WARN_MESSAGE.formatLines(plugin)) {
                            list.add(string.replace("%reason%", issueReason));
                        }
                        player.sendMessage(String.join("\n", list));
                        break;
                }
            }
        } else {
            Profile victimProfile = PlayerUtil.findPlayer(plugin, victim);
            if (victimProfile != null) {
                victimName = victimProfile.getName();
            }
        }

        String hover;
        if (isActive()) {
            List<String> list = new ArrayList<>();
            for (String string : Locale.PUNISHMENT_HOVER.formatLines(plugin)) {
                list.add(string
                        .replace("%type%", StringUtils.capitalize(type.toString().toLowerCase()))
                        .replace("%silentPrefix%", silentIssue ? Locale.SILENT_PREFIX.format(plugin) : "")
                        .replace("%victimName%", victimName)
                        .replace("%issuerName%", issuerName)
                        .replace("%reason%", this.issueReason));
            }
            if (!type.equals(Type.KICK) && !type.equals(Type.WARN)) list.add(Locale.PUNISHMENT_HOVER_TEMP.format(plugin)
                    .replace("%expiry%", this.expires == null ? "Never" : this.expires.toString()));

            hover = String.join("\n", list);
            WebHook.sendWebhook(plugin, type.toString(), victimName, issueReason, issuerName, null, this.expires == null ? "Never" : this.expires.toString());
        } else {
            List<String> list = new ArrayList<>();
            for (String string : Locale.UNPUNISHMENT_HOVER.formatLines(plugin)) {
                list.add(string
                        .replace("%type%", StringUtils.capitalize(type.toString().toLowerCase()))
                        .replace("%silentPrefix%", silentPardon ? Locale.SILENT_PREFIX.format(plugin) : "")
                        .replace("%victimName%", victimName)
                        .replace("%reason%", this.issueReason)
                        .replace("%issuerName%", issuerName)
                        .replace("%pardonReason%", pardonReason));
            }

            hover = String.join("\n", list);
            WebHook.sendWebhook(plugin, "UN" + type.toString(), victimName, issueReason, issuerName, pardonReason, null);
        }

        String typeString = type.equals(Type.KICK) || type.equals(Type.WARN) ? type.pastMessage() : (this.isActive() ? (this.expires == null ? "permanently " : "temporarily ") : "un") + type.pastMessage();

        String message = Locale.BROADCAST.format(plugin)
                .replace("%target%", victimName)
                .replace("%type%", typeString)
                .replace("%issuer%", issuerName);

        boolean silent = this.isActive() ? silentIssue : silentPardon;
        Notifications.sendMessage(plugin, silent, message, hover);
    }

    public void importFromDocument(Document d) {
        setVictim(d.get("victim", UUID.class));
        setIssuer(d.get("issuer", UUID.class));
        setPardoner(d.get("pardoner", UUID.class));

        setStack(d.getString("stack"));
        setIssueReason(d.getString("issue_reason"));
        setPardonReason(d.getString("pardon_reason"));
        setIssued(d.getDate("issued"));
        setExpires(d.getDate("expires"));
        setPardoned(d.getDate("pardoned"));
        setType(Type.valueOf(d.getString("type")));
        setSilentIssue(d.getBoolean("silent_issue"));
        setSilentPardon(d.getBoolean("silent_pardon"));
    }

    public Map<String, Object> export() {
        Map<String, Object> map = new HashMap<>();
        map.put("victim", victim);
        map.put("issuer", issuer);
        map.put("pardoner", pardoner);

        map.put("stack", stack);
        map.put("issue_reason", issueReason);
        map.put("pardon_reason", pardonReason);
        map.put("issued", issued);
        map.put("expires", expires);
        map.put("pardoned", pardoned);
        map.put("type", type.toString());
        map.put("silent_issue", silentIssue);
        map.put("silent_pardon", silentPardon);
        return map;
    }
}