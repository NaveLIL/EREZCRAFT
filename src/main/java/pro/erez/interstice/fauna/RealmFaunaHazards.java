package pro.erez.interstice.fauna;

import net.minecraft.world.entity.Entity;
import pro.erez.interstice.entity.CaveRiftSpiderEntity;

/** Local flora only: ordinary attacks, potions and ocean hazards keep their own rules. */
public final class RealmFaunaHazards {
    public static boolean adaptedToFlora(Entity entity) {
        return entity instanceof CaveRiftSpiderEntity || entity instanceof VaultHunter;
    }
    private RealmFaunaHazards() {}
}
