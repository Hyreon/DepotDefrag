package top.hyreon.depotDefrag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.HashMap;
import java.util.logging.Level;

public class LanguageLoader {

    DepotDefragPlugin plugin;
    HashMap<String, String> translationMap = new HashMap<>();

    public LanguageLoader(DepotDefragPlugin plugin) {

        this.plugin = plugin;

        File languageDirectory = new File(plugin.getDataFolder(), "languages/");
        if (!languageDirectory.isDirectory()) {
            languageDirectory.mkdir();
        }
        plugin.saveResource("languages/en_US.yml", true);
        plugin.saveResource("languages/en_UK.yml", false);
        File defaultLanguageFile = new File(plugin.getDataFolder(), "languages/en_US.yml");
        if (plugin.getConfig().getString("locale") != null && !plugin.getConfig().getString("locale").equals("en_US")) {
            Bukkit.getLogger().log(Level.INFO, "Loading custom lang file " + "languages/" + plugin.getConfig().getString("locale") + ".yml");
            FileConfiguration translations = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "languages/" + plugin.getConfig().getString("locale") + ".yml"));
            for (String translation : translations.getKeys(false)){
                translationMap.put(translation, translations.getString(translation));
            }
        } else {
            Bukkit.getLogger().log(Level.INFO, "Loading default lang file");
            FileConfiguration translations = YamlConfiguration.loadConfiguration(defaultLanguageFile);
            for (String translation : translations.getKeys(false)) {
                translationMap.put(translation, translations.getString(translation));
            }
        }

    }

    public String get(String path) {
        String result = "&x" + translationMap.get(path);
        result = result.replace("&x", plugin.getColor().toString());
        return ChatColor.translateAlternateColorCodes('&', result);
    }
}