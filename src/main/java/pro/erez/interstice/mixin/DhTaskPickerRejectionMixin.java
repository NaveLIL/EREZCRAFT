package pro.erez.interstice.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH3.3.3 immediately retries a rejected task while holding its shared picker lock.
 * Keep DH's original rejection handler and its queued task, then end just that
 * executor's inner dispatch loop so the remaining executors and callers can run.
 * This neither substitutes DH's scheduler nor changes its thread configuration.
 */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker",remap=false)
public abstract class DhTaskPickerRejectionMixin {
    @Unique private static final ThreadLocal<Boolean> interstice$rejectedDispatch=ThreadLocal.withInitial(()->false);
    @Unique private static volatile Method interstice$runTaskMethod;

    @Inject(method="startNextTask(Z)V",at=@At("HEAD"),remap=false)
    private void interstice$beginDispatch(boolean waitForLock,CallbackInfo ci) {
        interstice$rejectedDispatch.set(false);
    }

    @Redirect(method="startNextTask(Z)V",at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/core/util/threading/PriorityTaskPicker$Executor;runTask(Ljava/lang/Runnable;)V"),remap=false)
    private void interstice$observeRejectedDispatch(@Coerce Object executor,Runnable task) {
        try {
            Method run=interstice$runTaskMethod;
            if(run==null) {
                run=executor.getClass().getMethod("runTask",Runnable.class);
                interstice$runTaskMethod=run;
            }
            run.invoke(executor,task);
        } catch(InvocationTargetException dispatchFailure) {
            Throwable cause=dispatchFailure.getCause();
            if(cause instanceof RejectedExecutionException rejected) {
                interstice$rejectedDispatch.set(true);
                // DH catches this same exception and puts this same task back in its queue.
                throw rejected;
            }
            if(cause instanceof RuntimeException runtime) throw runtime;
            if(cause instanceof Error error) throw error;
            throw new IllegalStateException("Unexpected Distant Horizons task dispatch failure",cause);
        } catch(ReflectiveOperationException incompatible) {
            throw new IllegalStateException("Incompatible Distant Horizons dispatcher API; Interstice DH compatibility targets 3.3.3",incompatible);
        }
    }

    @Redirect(method="startNextTask(Z)V",at=@At(value="INVOKE",target="Ljava/util/Queue;poll()Ljava/lang/Object;"),remap=false)
    private Object interstice$deferRejectedRetry(Queue<?> queue) {
        if(interstice$rejectedDispatch.get()) {
            interstice$rejectedDispatch.set(false);
            return null;
        }
        return queue.poll();
    }
}
