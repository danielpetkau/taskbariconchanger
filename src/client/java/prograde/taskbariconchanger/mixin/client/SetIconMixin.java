package prograde.taskbariconchanger.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import prograde.taskbariconchanger.TaskbarIconChanger;
import prograde.taskbariconchanger.IconLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class SetIconMixin {

	@Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
	public void setIcon(CallbackInfo info) throws IOException {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		File pngFile = configDir.resolve("TaskbarIconChanger/icon.png").toFile();

		if (pngFile.exists()) {
			TaskbarIconChanger.LOGGER.info("Setting new taskbar icon");
			IconLoader.setWindowIcon(pngFile, MinecraftClient.getInstance());
			info.cancel();
		} else {
			TaskbarIconChanger.LOGGER.warn("TaskbarIconChanger/icon.png doesn't exist");
		}
	}
}