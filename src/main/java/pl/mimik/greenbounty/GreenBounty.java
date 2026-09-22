package pl.mimik.greenbounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GreenBounty extends JavaPlugin implements Listener {
    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private final Map<UUID, PendingBounty> pending = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey bountyKey;

    private static final String MAIN = ChatColor.DARK_GREEN + "GreenBounty";
    private static final String GUI = ChatColor.DARK_GREEN + "GreenBounty • Nagrody";

    @Override public void onEnable() {
        bountyKey = new NamespacedKey(this, "bounty_target");
        dataFile = new File(getDataFolder(), "bounties.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        loadBounties();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("greenbounty")).setExecutor(new BountyCommand(this));
        getLogger().info("GreenBounty enabled.");
    }

    @Override public void onDisable() { saveBounties(); }

    public void openRewardGui(Player p, Player target) {
        PendingBounty old = pending.remove(p.getUniqueId());
        if (old != null) returnItems(p, old.rewards);
        Inventory inv = Bukkit.createInventory(null, 27, GUI + " • " + target.getName());
        // Reward slots intentionally start empty so players can place real items from their inventory.
        inv.setItem(4, button(Material.NAME_TAG, ChatColor.GREEN + "Cel: " + target.getName(), "Maksymalnie 3 nagrody"));
        inv.setItem(22, button(Material.LIME_DYE, ChatColor.GREEN + "ZLEĆ BOUNTY", "Kliknij, aby zatwierdzić"));
        inv.setItem(26, button(Material.BARRIER, ChatColor.RED + "Anuluj", "Zwróć itemy i zamknij"));
        p.openInventory(inv);
        pending.put(p.getUniqueId(), new PendingBounty(target.getUniqueId(), inv));
    }

    private ItemStack button(Material mat, String name, String lore) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta();
        m.setDisplayName(name); m.setLore(List.of(ChatColor.GRAY + lore)); i.setItemMeta(m); return i;
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        PendingBounty pb = pending.get(p.getUniqueId());
        if (pb == null || !e.getView().getTitle().startsWith(GUI)) return;
        int slot = e.getRawSlot();
        if (slot == 10 || slot == 13 || slot == 16) return; // reward slots
        // Prevent shift-clicking arbitrary items into the GUI except the three reward slots.
        if (e.isShiftClick()) { e.setCancelled(true); return; }
        if (slot == 22) {
            e.setCancelled(true);
            List<ItemStack> rewards = new ArrayList<>();
            for (int s : new int[]{10,13,16}) {
                ItemStack item = e.getInventory().getItem(s);
                if (item != null && item.getType() != Material.AIR && !isButton(item)) rewards.add(item.clone());
            }
            if (rewards.isEmpty()) { p.sendMessage(MAIN + ChatColor.RED + " Włóż przynajmniej jedną nagrodę."); return; }
            if (bounties.containsKey(pb.target)) { p.sendMessage(MAIN + ChatColor.RED + " Ten gracz ma już aktywne bounty."); return; }
            if (pb.target.equals(p.getUniqueId())) { p.sendMessage(MAIN + ChatColor.RED + " Nie możesz zlecić bounty na siebie."); return; }
            // Remove only the reward items from the GUI; the GUI slots contain the actual item stacks.
            bounties.put(pb.target, new Bounty(pb.target, p.getUniqueId(), rewards));
            for (int s : new int[]{10,13,16}) e.getInventory().setItem(s, null);
            saveBounties();
            p.closeInventory();
            p.sendMessage(MAIN + ChatColor.GREEN + " Zlecenie zostało ustawione na " + Bukkit.getOfflinePlayer(pb.target).getName() + ".");
            Bukkit.broadcastMessage(MAIN + ChatColor.YELLOW + " Nowe bounty! Cel: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(pb.target).getName() + ChatColor.YELLOW + " • nagród: " + rewards.size());
        } else if (slot == 26) {
            e.setCancelled(true); p.closeInventory();
        } else if (slot < e.getView().getTopInventory().getSize()) {
            e.setCancelled(true);
        }
    }

    private boolean isButton(ItemStack i) { return i.getType() == Material.NAME_TAG || i.getType() == Material.LIME_DYE || i.getType() == Material.BARRIER; }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        PendingBounty pb = pending.remove(p.getUniqueId());
        if (pb == null) return;
        if (e.getInventory().getItem(22) != null && e.getInventory().getItem(22).getType() == Material.LIME_DYE) {
            List<ItemStack> rewards = extractRewards(e.getInventory());
            returnItems(p, rewards);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        PendingBounty pb = pending.remove(e.getPlayer().getUniqueId());
        if (pb != null) returnItems(e.getPlayer(), extractRewards(pb.inventory));
    }

    private List<ItemStack> extractRewards(Inventory inv) {
        List<ItemStack> out = new ArrayList<>();
        for (int s : new int[]{10,13,16}) { ItemStack i = inv.getItem(s); if (i != null && i.getType()!=Material.AIR && !isButton(i)) out.add(i.clone()); }
        return out;
    }

    private void returnItems(Player p, List<ItemStack> items) { for (ItemStack i : items) { Map<Integer,ItemStack> left=p.getInventory().addItem(i); left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x)); } }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player target = e.getEntity();
        Bounty b = bounties.remove(target.getUniqueId());
        if (b == null) return;
        Player killer = target.getKiller();
        if (killer == null || killer.getUniqueId().equals(b.target)) {
            bounties.put(b.target, b); // keep bounty if no valid killer
            return;
        }
        for (ItemStack reward : b.rewards) {
            Map<Integer,ItemStack> left = killer.getInventory().addItem(reward.clone());
            left.values().forEach(x -> killer.getWorld().dropItemNaturally(killer.getLocation(), x));
        }
        killer.sendMessage(MAIN + ChatColor.GREEN + " Wykonałeś bounty na " + target.getName() + "!");
        Bukkit.broadcastMessage(MAIN + ChatColor.GOLD + " " + killer.getName() + " wykonał bounty na " + target.getName() + "!");
        saveBounties();
    }

    public Map<UUID,Bounty> getBounties(){ return Collections.unmodifiableMap(bounties); }
    public void removeBounty(UUID target){ bounties.remove(target); saveBounties(); }

    private void loadBounties() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec=data.getConfigurationSection("bounties"); if(sec==null)return;
        for(String key:sec.getKeys(false)) try {
            UUID target=UUID.fromString(key), owner=UUID.fromString(sec.getString(key+".owner"));
            List<ItemStack> rewards=new ArrayList<>(); for(int i=0;i<3;i++){ ItemStack item=sec.getItemStack(key+".reward"+i); if(item!=null) rewards.add(item); }
            bounties.put(target,new Bounty(target,owner,rewards));
        }catch(Exception ignored){}
    }

    private void saveBounties() {
        if(data==null)return; data.set("bounties",null);
        for(Bounty b:bounties.values()){String k="bounties."+b.target; data.set(k+".owner",b.owner.toString()); for(int i=0;i<b.rewards.size()&&i<3;i++)data.set(k+".reward"+i,b.rewards.get(i));}
        try{data.save(dataFile);}catch(IOException ex){getLogger().severe("Could not save bounties.yml: "+ex.getMessage());}
    }

    static final class Bounty { final UUID target,owner; final List<ItemStack> rewards; Bounty(UUID t,UUID o,List<ItemStack> r){target=t;owner=o;rewards=new ArrayList<>(r);} }
    static final class PendingBounty { final UUID target; final Inventory inventory; PendingBounty(UUID t,Inventory i){target=t;inventory=i;} }
}
