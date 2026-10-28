package pro.erez.avatar.mixin;

import java.util.Collections;
import java.util.Map;
import com.tom.cpl.config.ConfigEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep CPM's backing maps and serialization ownership, make lazy map operations atomic. */
@Mixin(value=ConfigEntry.class,remap=false)
public abstract class ConfigEntryMixin {
    @Shadow protected Map<String,ConfigEntry> entries;
    @Shadow protected Map<String,ConfigEntry.ConfigEntryList> lists;
    @Shadow protected Map<String,Object> data;

    @Inject(method="loadConfig",at=@At("HEAD"))
    private void erez$guardLazyEntries(CallbackInfo ci) {
        entries=Collections.synchronizedMap(entries);
        lists=Collections.synchronizedMap(lists);
        if(data!=null)data=Collections.synchronizedMap(data);
    }
}
