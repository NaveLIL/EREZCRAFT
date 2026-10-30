package pro.erez.interstice.vehicle;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;

/** Server-owned access state. PIN material and crew grants never enter update packets. */
public final class VehicleSecurity {
    private UUID owner;
    private byte[] salt=new byte[0],hash=new byte[0];
    private boolean configured;
    private final LinkedHashSet<UUID> crew=new LinkedHashSet<>();
    private record Attempt(int failures,long lockedUntil){}
    private final Map<UUID,Attempt> attempts=new LinkedHashMap<>();
    private long maintenanceUntil;
    public UUID owner(){return owner;}
    public boolean configured(){return configured;}
    public boolean owner(Player p){return owner!=null&&(owner.equals(p.getUUID())||p.hasPermissions(4));}
    public boolean allowed(Player p){return owner(p)||owner!=null&&crew.contains(p.getUUID());}
    public void claim(UUID id){if(owner!=null)throw new IllegalStateException("Vehicle already owned");owner=Objects.requireNonNull(id);}
    public boolean changePin(Player p,String pin){if(!owner(p)||!pin.matches("[0-9]{6}"))return false;salt=new byte[16];new SecureRandom().nextBytes(salt);hash=digest(salt,pin);configured=true;crew.clear();attempts.clear();maintenanceUntil=0;return true;}
    public boolean unlock(Player p,String pin,long tick){
        if(allowed(p))return true;
        var old=attempts.getOrDefault(p.getUUID(),new Attempt(0,0));
        if(tick<old.lockedUntil||!configured||salt.length!=16||hash.length!=32||!pin.matches("[0-9]{6}"))return false;
        if(MessageDigest.isEqual(hash,digest(salt,pin))){if(crew.size()>=64)crew.remove(crew.iterator().next());crew.add(p.getUUID());attempts.remove(p.getUUID());return true;}
        if(attempts.size()>=128&&!attempts.containsKey(p.getUUID()))attempts.remove(attempts.keySet().iterator().next());
        int failures=old.failures+1;attempts.put(p.getUUID(),new Attempt(failures>=5?0:failures,tick+(failures>=5?1200:10)));return false;
    }
    public boolean maintenance(Player p,long tick){return owner(p)&&maintenanceUntil>tick;}
    public boolean maintenance(long tick){return maintenanceUntil>tick;}
    public boolean toggleMaintenance(Player p,long tick){if(!owner(p))return false;maintenanceUntil=maintenanceUntil>tick?0:tick+2400;return true;}
    private static byte[] digest(byte[] salt,String pin){try{var d=MessageDigest.getInstance("SHA-256");d.update(salt);return d.digest(pin.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public CompoundTag save(){var t=new CompoundTag();if(owner!=null)t.putUUID("Owner",owner);t.putBoolean("Configured",configured);t.putByteArray("Salt",salt);t.putByteArray("Hash",hash);var list=new ListTag();for(var id:crew)list.add(net.minecraft.nbt.NbtUtils.createUUID(id));t.put("Crew",list);return t;}
    public CompoundTag publicTag(long tick){var t=new CompoundTag();t.putBoolean("Public",true);if(owner!=null)t.putUUID("Owner",owner);t.putBoolean("Configured",configured);t.putLong("MaintenanceUntil",maintenanceUntil>tick?maintenanceUntil:0);return t;}
    public void load(CompoundTag t){owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;configured=t.getBoolean("Configured");crew.clear();attempts.clear();maintenanceUntil=t.getBoolean("Public")?t.getLong("MaintenanceUntil"):0;salt=t.getByteArray("Salt");hash=t.getByteArray("Hash");var list=t.getList("Crew",Tag.TAG_INT_ARRAY);for(int i=0;i<Math.min(64,list.size());i++)try{crew.add(NbtUtils.loadUUID(list.get(i)));}catch(RuntimeException ignored){} }
}
