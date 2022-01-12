package io.github.punishmentsx.profiles;

import com.google.gson.JsonObject;
import io.github.punishmentsx.Locale;
import io.github.punishmentsx.PunishmentsX;
import io.github.punishmentsx.database.mongo.MongoDeserializedResult;
import io.github.punishmentsx.database.mongo.MongoUpdate;
import io.github.punishmentsx.database.redis.RedisAction;
import io.github.punishmentsx.database.redis.RedisMessage;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {

    private final PunishmentsX plugin;
    private @Getter Map<UUID, Profile> profiles;
    public ProfileManager(PunishmentsX plugin) {
        this.plugin = plugin;
        this.profiles = new HashMap<>();
    }

    public Profile createProfile(UUID uuid) {
        Profile profile = new Profile(plugin, uuid);
        profiles.put(profile.getUuid(), profile);

        push(true, profile, false);
        return profile;
    }

    public Profile find(UUID uuid, boolean store) {
        final Profile[] profile = {profiles.get(uuid)};
        if(profile[0] == null) {
            pull(false, uuid, store, mdr -> {
                if(mdr instanceof Profile) {
                    profile[0] = (Profile) mdr;
                }
            });
        }
        return profile[0];
    }

    public Profile find(String name, boolean store) {
        Profile[] profile = {null};

        pull(false, name, store, mdr -> {
            if(mdr instanceof Profile) {
                profile[0] = (Profile) mdr;
            }
        });

        return profile[0];
    }

    public Profile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public void pull(boolean async, String name, boolean store, MongoDeserializedResult mdr) {
        plugin.getMongo().getDocument(false, "profiles", "name", name, document -> {
            if(document != null) {
                UUID uuid = UUID.fromString(document.getString("_id"));
                Profile profile = new Profile(plugin, uuid);
                profile.importFromDocument(document);

                for(UUID u : profile.getPunishments()) {
                    plugin.getPunishmentManager().pull(false, u, true, obj -> {});
                }

                mdr.call(profile);
                if(store) {
                    profiles.put(profile.getUuid(), profile);
                }
            } else {
                mdr.call(null);
            }
        });
    }

    public void pull(boolean async, UUID uuid, boolean store, MongoDeserializedResult mdr) {
        plugin.getMongo().getDocument(async, "profiles", "_id", uuid, document -> {
            if(document != null) {
                Profile profile = new Profile(plugin, uuid);
                profile.importFromDocument(document);

                for(UUID u : profile.getPunishments()) {
                    plugin.getPunishmentManager().pull(false, u, true, obj -> {});
                }

                mdr.call(profile);
                if(store) {
                    profiles.put(profile.getUuid(), profile);
                }
            } else {
                mdr.call(null);
            }
        });
    }

    public void push(boolean async, Profile profile, boolean unload) {
        MongoUpdate mu = new MongoUpdate("profiles", profile.getUuid());
        mu.setUpdate(profile.export());
        plugin.getMongo().massUpdate(async, mu);

        if (plugin.getConfig().getBoolean("DATABASE.REDIS.ENABLED")) {
            JsonObject json = new JsonObject();
            json.addProperty("action", RedisAction.PROFILE_UPDATE.toString());
            json.addProperty("fromServer", plugin.getConfig().getString("GENERAL.SERVER_NAME"));
            json.addProperty("uuid", profile.getUuid().toString());

            plugin.getRedisPublisher().getMessageQueue().add(new RedisMessage(Locale.REDIS_CHANNEL.format(plugin), json));
        }

        if(unload) {
            profiles.remove(profile.getUuid());
        }
    }

    public void update() {
        for(Profile profile : profiles.values()) {
            profile.update();
        }
    }

    public void shutdown() {
        HashSet<Profile> toRemove = new HashSet<>();

        for(Profile profile : profiles.values()) {
            Player player = profile.getPlayer();
            if(player != null && player.isOnline()) {
                toRemove.add(profile);
            }
        }

        toRemove.forEach(profile -> push(false, profile, true));
    }
}