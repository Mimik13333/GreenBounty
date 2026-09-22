package pl.mimik.greenbounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GreenBounty extends JavaPlugin implements Listener {

    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private final Map<UUID, PendingBounty> pending = new HashMap<>();

    private File dataFile;
    private YamlConfiguration data;

    private static final String MAIN = ChatColor.DARK_GREEN + "GreenBounty";
    private static final String GUI = ChatColor.DARK_GREEN + "GreenBounty";

    private static final int[] REWARD_SLOTS = {10, 13, 16};

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "bounties.yml");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadBounties();

        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("greenbounty"))
                .setExecutor(new BountyCommand(this));

        getLogger().info("GreenBounty enabled.");
    }

    @Override
    public void onDisable() {
        saveBounties();
    }

    public void openRewardGui(Player player, Player target) {

        PendingBounty old = pending.remove(player.getUniqueId());

        if (old != null) {
            returnItems(player, extractRewards(old.inventory));
        }

        Inventory inv = Bukkit.createInventory(
                null,
                27,
                GUI + " • " + target.getName()
        );

        inv.setItem(
                4,
                button(
                        Material.NAME_TAG,
                        ChatColor.GREEN + "Cel: " + target.getName(),
                        "Maksymalnie 3 nagrody"
                )
        );

        inv.setItem(
                22,
                button(
                        Material.LIME_DYE,
                        ChatColor.GREEN + "ZLEĆ BOUNTY",
                        "Kliknij, aby zatwierdzić"
                )
        );

        inv.setItem(
                26,
                button(
                        Material.BARRIER,
                        ChatColor.RED + "Anuluj",
                        "Zwróć itemy i zamknij"
                )
        );

        pending.put(
                player.getUniqueId(),
                new PendingBounty(target.getUniqueId(), inv)
        );

        player.openInventory(inv);
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(ChatColor.GRAY + lore));
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PendingBounty pb = pending.get(player.getUniqueId());

        if (pb == null || !event.getView().getTitle().startsWith(GUI)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot >= event.getView().getTopInventory().getSize()) {

            if (event.isShiftClick()) {
                event.setCancelled(true);
            }

            return;
        }

        if (isRewardSlot(slot)) {
            return;
        }

        if (slot == 22) {

            event.setCancelled(true);

            if (pb.target.equals(player.getUniqueId())) {
                player.sendMessage(
                        MAIN + ChatColor.RED +
                                " Nie możesz zlecić bounty na siebie!"
                );
                return;
            }

            if (bounties.containsKey(pb.target)) {
                player.sendMessage(
                        MAIN + ChatColor.RED +
                                " Ten gracz ma już aktywne bounty!"
                );
                return;
            }

            List<ItemStack> rewards =
                    extractRewards(event.getInventory());

            if (rewards.isEmpty()) {
                player.sendMessage(
                        MAIN + ChatColor.RED +
                                " Włóż przynajmniej jedną nagrodę!"
                );
                return;
            }

            if (rewards.size() > 3) {
                player.sendMessage(
                        MAIN + ChatColor.RED +
                                " Maksymalnie 3 nagrody!"
                );
                return;
            }

            bounties.put(
                    pb.target,
                    new Bounty(
                            pb.target,
                            player.getUniqueId(),
                            rewards
                    )
            );

            for (int rewardSlot : REWARD_SLOTS) {
                event.getInventory().setItem(rewardSlot, null);
            }

            saveBounties();

            player.closeInventory();

            String targetName =
                    Bukkit.getOfflinePlayer(pb.target).getName();

            player.sendMessage(
                    MAIN + ChatColor.GREEN +
                            " Zleciłeś bounty na " +
                            targetName + "!"
            );

            Bukkit.broadcastMessage(
                    MAIN + ChatColor.YELLOW +
                            " Nowe bounty na " +
                            ChatColor.WHITE +
                            targetName +
                            ChatColor.YELLOW +
                            "!"
            );

            return;
        }

        if (slot == 26) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PendingBounty pb = pending.get(player.getUniqueId());

        if (pb == null || !event.getView().getTitle().startsWith(GUI)) {
            return;
        }

        for (int slot : event.getRawSlots()) {

            if (slot < event.getView().getTopInventory().getSize()
                    && !isRewardSlot(slot)) {

                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isRewardSlot(int slot) {
        return slot == 10 || slot == 13 || slot == 16;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        PendingBounty pb =
                pending.remove(player.getUniqueId());

        if (pb == null) {
            return;
        }

        List<ItemStack> rewards =
                extractRewards(event.getInventory());

        if (!rewards.isEmpty()) {
            returnItems(player, rewards);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        PendingBounty pb =
                pending.remove(player.getUniqueId());

        if (pb != null) {
            returnItems(
                    player,
                    extractRewards(pb.inventory)
            );
        }
    }

    private List<ItemStack> extractRewards(Inventory inventory) {

        List<ItemStack> rewards = new ArrayList<>();

        for (int slot : REWARD_SLOTS) {

            ItemStack item = inventory.getItem(slot);

            if (item != null &&
                    item.getType() != Material.AIR) {

                rewards.add(item.clone());
            }
        }

        return rewards;
    }

    private void returnItems(
            Player player,
            List<ItemStack> items
    ) {

        for (ItemStack item : items) {

            Map<Integer, ItemStack> left =
                    player.getInventory().addItem(item);

            for (ItemStack remaining : left.values()) {

                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        remaining
                );
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {

        Player target = event.getEntity();

        Bounty bounty =
                bounties.remove(target.getUniqueId());

        if (bounty == null) {
            return;
        }

        Player killer = target.getKiller();

        if (killer == null ||
                killer.getUniqueId().equals(bounty.target)) {

            bounties.put(
                    bounty.target,
                    bounty
            );

            return;
        }

        for (ItemStack reward : bounty.rewards) {

            Map<Integer, ItemStack> left =
                    killer.getInventory().addItem(
                            reward.clone()
                    );

            for (ItemStack remaining : left.values()) {

                killer.getWorld().dropItemNaturally(
                        killer.getLocation(),
                        remaining
                );
            }
        }

        killer.sendMessage(
                MAIN + ChatColor.GREEN +
                        " Wykonałeś bounty na " +
                        target.getName() + "!"
        );

        Bukkit.broadcastMessage(
                MAIN + ChatColor.GOLD +
                        " " + killer.getName() +
                        " wykonał bounty na " +
                        target.getName() + "!"
        );

        saveBounties();
    }

    public Map<UUID, Bounty> getBounties() {
        return Collections.unmodifiableMap(bounties);
    }

    public void removeBounty(UUID target) {
        bounties.remove(target);
        saveBounties();
    }

    private void loadBounties() {

        data =
                YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection section =
                data.getConfigurationSection("bounties");

        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {

            try {

                UUID target =
                        UUID.fromString(key);

                UUID owner =
                        UUID.fromString(
                                section.getString(
                                        key + ".owner"
                                )
                        );

                List<ItemStack> rewards =
                        new ArrayList<>();

                for (int i = 0; i < 3; i++) {

                    ItemStack item =
                            section.getItemStack(
                                    key + ".reward" + i
                            );

                    if (item != null &&
                            item.getType() != Material.AIR) {

                        rewards.add(item);
                    }
                }

                bounties.put(
                        target,
                        new Bounty(
                                target,
                                owner,
                                rewards
                        )
                );

            } catch (Exception ignored) {
            }
        }
    }

    private void saveBounties() {

        if (data == null) {
            return;
        }

        data.set("bounties", null);

        for (Bounty bounty : bounties.values()) {

            String key =
                    "bounties." + bounty.target;

            data.set(
                    key + ".owner",
                    bounty.owner.toString()
            );

            for (
                    int i = 0;
                    i < bounty.rewards.size() && i < 3;
                    i++
            ) {

                data.set(
                        key + ".reward" + i,
                        bounty.rewards.get(i)
                );
            }
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe(
                    "Could not save bounties.yml: " +
                            exception.getMessage()
            );
        }
    }

    static final class Bounty {

        final UUID target;
        final UUID owner;
        final List<ItemStack> rewards;

        Bounty(
                UUID target,
                UUID owner,
                List<ItemStack> rewards
        ) {
            this.target = target;
            this.owner = owner;
            this.rewards =
                    new ArrayList<>(rewards);
        }
    }

    static final class PendingBounty {

        final UUID target;
        final Inventory inventory;

        PendingBounty(
                UUID target,
                Inventory inventory
        ) {
            this.target = target;
            this.inventory = inventory;
        }
    }
}
