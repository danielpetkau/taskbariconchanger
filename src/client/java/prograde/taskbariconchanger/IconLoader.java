package prograde.taskbariconchanger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;

/*
 * Taken from itlt and modified to work with fabric
 */

public class IconLoader {
    public static void setWindowIcon(final File inputIconFile, final MinecraftClient mcInstance) throws IOException {
        final List<InputStream> iconsList = new ArrayList<>();
        List<BufferedImage> bufferedImageList = new ArrayList<>(0);

        final String inputIconFilenameAndExt = inputIconFile.getName().toLowerCase();
        if (inputIconFilenameAndExt.endsWith(".png")) {
            // load the `.png` file directly as an `InputStream`
            iconsList.add(new FileInputStream(inputIconFile));
        }

        TaskbarIconChanger.LOGGER.debug("Icon file: \"" + inputIconFilenameAndExt + "\"");

        if (inputIconFilenameAndExt.endsWith(".ico") || inputIconFilenameAndExt.endsWith(".icns")) {
            // convert from List<BufferedImage> to List<InputStream> and filter out invalid image types
            for (final BufferedImage bufferedImage : bufferedImageList) {
                TaskbarIconChanger.LOGGER.debug("---");
                TaskbarIconChanger.LOGGER.debug("Type: " + bufferedImage.getType());
                TaskbarIconChanger.LOGGER.debug("Width: " + bufferedImage.getWidth());
                TaskbarIconChanger.LOGGER.debug("Height: " + bufferedImage.getHeight());
                TaskbarIconChanger.LOGGER.debug("Transparency: " + bufferedImage.getTransparency());

                // only convert icons that are 8bit per channel, non-premultiplied RGBA as that's what GLFW expects.
                // icons that aren't converted are not included in the List<InputStream>
                if (bufferedImage.getType() == BufferedImage.TYPE_INT_ARGB) {

                    // handle special case for ICNS where icon sizes above 48px aren't being properly decoded yet
                    if (inputIconFilenameAndExt.endsWith(".icns")) {
                        if (bufferedImage.getWidth() <= 48) {
                            iconsList.add(convertToInputStream(bufferedImage));
                            TaskbarIconChanger.LOGGER.debug("Added embedded image");
                        } else {
                            TaskbarIconChanger.LOGGER.debug("Skipped embedded image");
                        }
                    } else {
                        iconsList.add(convertToInputStream(bufferedImage));
                        TaskbarIconChanger.LOGGER.debug("Added embedded image");
                    }

                } else {
                    TaskbarIconChanger.LOGGER.debug("Skipped embedded image");
                }
            }
        }

        TaskbarIconChanger.LOGGER.debug("Final iconsList size: " + iconsList.size());

        final GLFWImage.Buffer buffer = loadIconsIntoBuffer(iconsList, mcInstance);
        GLFW.glfwSetWindowIcon(mcInstance.getWindow().getHandle(), buffer);
    }

    public static GLFWImage.Buffer loadIconsIntoBuffer(final List<InputStream> iconsList,
                                                       final MinecraftClient mcInstance) throws IOException {
        return loadIconsIntoBuffer(iconsList, mcInstance, 1);
    }

    public static GLFWImage.Buffer loadIconsIntoBuffer(final List<InputStream> iconsList, final MinecraftClient mcInstance,
                                                       final int attempt) throws IOException {
        final MemoryStack memoryStack = MemoryStack.stackPush();
        final GLFWImage.Buffer buffer = GLFWImage.malloc(iconsList.size(), memoryStack);

        final IntBuffer intBufferX = memoryStack.mallocInt(1);
        final IntBuffer intBufferY = memoryStack.mallocInt(1);
        final IntBuffer intBufferChannels = memoryStack.mallocInt(1);

        // load each icon in the iconsList and append it to the GLFWImage.Buffer, keeping track of any errors that
        // may occur when trying to load each icon
        short iconCounter = 0;
        short errorCounter = 0;
        List<InputStream> newIconsList = new ArrayList<>();
        for (final InputStream inStream : iconsList) {
            final ByteBuffer byteBuffer;
            try {
                byteBuffer = readIconPixels(inStream, intBufferX, intBufferY, intBufferChannels);
                if (byteBuffer == null) throw new IOException("byteBuffer is null");
            } catch (final IOException e) {
                TaskbarIconChanger.LOGGER.debug("Unable to load image #" + iconCounter + " inside iconsList, skipping...");
                errorCounter++;
                continue;
            }
            buffer.position(iconCounter);
            buffer.width(intBufferX.get(0));
            buffer.height(intBufferY.get(0));
            buffer.pixels(byteBuffer);
            iconCounter++;
            newIconsList.add(inStream);
        }

        if (errorCounter == iconsList.size()) {
            // if there was an error loading all of the icons inside the .ico/.icns, throw an error and don't try setting the
            // window icon as an empty buffer.
            throw new IOException("Unable to load icon(s): Failed to load all embedded images");
        }

        // GLFW expects the allocated stack capacity to match the used capacity otherwise it'll crash
        if (errorCounter > 0) {
            if (attempt > 2) {
                throw new IOException("Unable to load icon(s): Too many failed attempts");
            } else {
                // Allocate a new stack without erroneous icons and use that instead
                return loadIconsIntoBuffer(newIconsList, mcInstance, attempt + 1);
            }
        } else {
            buffer.position(0);
            return buffer;
        }
    }

    // Currently this is 1.19.4 logic
    // In 1.20 MC changed so there is no separate load method anymore
    private static ByteBuffer readIconPixels(InputStream pTextureStream, IntBuffer pX, IntBuffer pY, IntBuffer pChannelInFile) throws IOException {
        //removed for 1.21 compatability, shouldn't be needed
        //RenderSystem.assertInInitPhase();
        ByteBuffer bytebuffer = null;

        ByteBuffer bytebuffer1;
        try {
            bytebuffer = TextureUtil.readResource(pTextureStream);
            bytebuffer.rewind();
            bytebuffer1 = STBImage.stbi_load_from_memory(bytebuffer, pX, pY, pChannelInFile, 0);
        } finally {
            if (bytebuffer != null) {
                MemoryUtil.memFree(bytebuffer);
            }

        }

        return bytebuffer1;
    }

    public static ByteArrayInputStream convertToInputStream(final BufferedImage bufferedImage) throws IOException {
        // convert BufferedImage to ByteArrayOutputStream without a double copy behind the scenes, only for our
        // specific use-case. This is faster and more efficient, but can be problematic when performing other operations
        // on the same instance of a ByteArrayOutputStream or BufferedImage.
        // https://stackoverflow.com/a/12253091/3944931 (warning: unsafe - not suitable for all use-cases!)
        final ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public synchronized byte[] toByteArray() {
                return this.buf;
            }
        };
        ImageIO.write(bufferedImage, "png", output);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray(), 0, output.size());
        bufferedImage.flush();
        return inputStream;
    }
}