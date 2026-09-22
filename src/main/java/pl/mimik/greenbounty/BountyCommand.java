package pl.mimik.greenbounty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class BountyCommand implements CommandExecutor, TabCompleter {
    private final GreenBounty plugin;
    public BountyCommand(GreenBounty p){plugin=p; Objects.requireNonNull(p.getCommand("greenbounty")).setTabCompleter(this);}

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a){
        if(!(s instanceof Player p)){s.sendMessage("Komenda tylko dla gracza.");return true;}
        if(a.length==0){p.sendMessage(ChatColor.DARK_GREEN+"/greenbounty zleć <gracz>");p.sendMessage(ChatColor.DARK_GREEN+"/greenbounty moje | top | info <gracz>");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "zleć": case "zlec":
                if(a.length<2){p.sendMessage(ChatColor.RED+"Użycie: /greenbounty zleć <gracz>");return true;}
                Player t=Bukkit.getPlayerExact(a[1]); if(t==null){p.sendMessage(ChatColor.RED+"Gracz musi być online.");return true;}
                if(t.equals(p)){p.sendMessage(ChatColor.RED+"Nie możesz zlecić bounty na siebie.");return true;}
                if(plugin.getBounties().containsKey(t.getUniqueId())){p.sendMessage(ChatColor.RED+"Ten gracz ma już aktywne bounty.");return true;}
                plugin.openRewardGui(p,t); return true;
            case "moje":
                long mine=plugin.getBounties().values().stream().filter(b->b.owner.equals(p.getUniqueId())).count(); p.sendMessage(ChatColor.GREEN+"Twoje aktywne bounty: "+mine); return true;
            case "top":
                p.sendMessage(ChatColor.GREEN+"TOP poszukiwanych:"); plugin.getBounties().values().stream().limit(10).forEach(b->p.sendMessage("§7- §f"+name(b.target)+" §7("+b.rewards.size()+" nagród)")); return true;
            case "info":
                if(a.length<2){p.sendMessage(ChatColor.RED+"Użycie: /greenbounty info <gracz>");return true;}
                Player info=Bukkit.getPlayerExact(a[1]); if(info==null){p.sendMessage(ChatColor.RED+"Gracz musi być online.");return true;}
                GreenBounty.Bounty b=plugin.getBounties().get(info.getUniqueId()); if(b==null){p.sendMessage(ChatColor.GRAY+"Brak aktywnego bounty.");} else p.sendMessage(ChatColor.GREEN+"Bounty na "+info.getName()+": "+b.rewards.size()+" nagród."); return true;
            case "usun":
                if(!p.hasPermission("greenbounty.admin")){p.sendMessage(ChatColor.RED+"Brak uprawnień.");return true;}
                if(a.length<2){p.sendMessage(ChatColor.RED+"Użycie: /greenbounty usun <gracz>");return true;}
                Player u=Bukkit.getPlayerExact(a[1]); if(u!=null)plugin.removeBounty(u.getUniqueId()); p.sendMessage(ChatColor.GREEN+"Usunięto bounty."); return true;
            case "wyczysc":
                if(!p.hasPermission("greenbounty.admin")){p.sendMessage(ChatColor.RED+"Brak uprawnień.");return true;}
                plugin.getBounties().keySet().toArray(UUID[]::new); for(UUID id:new ArrayList<>(plugin.getBounties().keySet())) plugin.removeBounty(id); p.sendMessage(ChatColor.GREEN+"Wyczyszczono bounty."); return true;
            case "reload":
                if(!p.hasPermission("greenbounty.admin")){p.sendMessage(ChatColor.RED+"Brak uprawnień.");return true;} p.sendMessage(ChatColor.GREEN+"GreenBounty przeładowany."); return true;
            default: p.sendMessage(ChatColor.RED+"Nieznana komenda."); return true;
        }
    }
    private String name(UUID id){OfflinePlayer o=Bukkit.getOfflinePlayer(id);return o.getName()==null?id.toString():o.getName();}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(a.length==1)return List.of("zleć","moje","top","info","usun","wyczysc","reload"); if(a.length==2&&List.of("zleć","zlec","info","usun").contains(a[0].toLowerCase()))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();return List.of();}
}
