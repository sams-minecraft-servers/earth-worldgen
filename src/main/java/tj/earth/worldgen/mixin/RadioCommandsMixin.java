package tj.earth.worldgen.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.Radio;

/** Adds /radio wherever the game builds its own commands (at start and on every reload), when the radio is on. */
@Mixin(Commands.class)
abstract class RadioCommandsMixin {
    @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void earth$radio(final CallbackInfo callback) {
        Radio.register(this.dispatcher);
    }
}
