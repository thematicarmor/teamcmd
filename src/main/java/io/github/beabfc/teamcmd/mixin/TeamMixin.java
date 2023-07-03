package io.github.beabfc.teamcmd.mixin;

import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Team.class)
public class TeamMixin {

    @Shadow
    private Text displayName;
    @Final
    @Shadow
    private Style nameStyle;
    @Shadow
    private Text prefix;
    @Shadow
    private Text suffix;


    @Inject(at = @At("RETURN"), method = "getFormattedName", cancellable = true)
    public final void getFormattedName(CallbackInfoReturnable<MutableText> cir) {
        MutableText mutableText = Texts.bracketed(this.displayName.copy().fillStyle(this.nameStyle));
        cir.setReturnValue(mutableText);
    }

    @Inject(at = @At("RETURN"), method = "decorateName(Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", cancellable = true)
    public final void decorateName(Text name, CallbackInfoReturnable<MutableText> cir) {
        MutableText mutableText = Text.empty().append(this.prefix).append(name).append(this.suffix);
        cir.setReturnValue(mutableText);
    }

}
