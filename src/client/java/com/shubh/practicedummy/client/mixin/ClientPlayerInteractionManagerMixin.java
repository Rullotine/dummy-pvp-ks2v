package com.shubh.practicedummy.client.mixin;

import com.shubh.practicedummy.client.dummy.DummyCombat;
import com.shubh.practicedummy.client.dummy.PracticeDummy;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps every interaction with a dummy local: the server doesn't know the entity id, so we never
 * send attack/interact packets for it (nothing odd shows up for anti-cheats or server logs).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

	@Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
	private void practicedummy$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
		if (target instanceof PracticeDummy dummy) {
			DummyCombat.hit(player, dummy);
			ci.cancel();
		}
	}

	/** Spear jab. Vanilla still sends its normal packet; we additionally check the dummies locally. */
	@Inject(method = "attackWithPiercingWeapon", at = @At("HEAD"))
	private void practicedummy$jab(PiercingWeaponComponent component, CallbackInfo ci) {
		DummyCombat.jab();
	}

	// PASS = "nothing happened", so right-clicking a dummy just falls through to using your held item.
	@Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
	private void practicedummy$interact(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (entity instanceof PracticeDummy) cir.setReturnValue(ActionResult.PASS);
	}

	@Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
	private void practicedummy$interactAt(PlayerEntity player, Entity entity, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (entity instanceof PracticeDummy) cir.setReturnValue(ActionResult.PASS);
	}
}
