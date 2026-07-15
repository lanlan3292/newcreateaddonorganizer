package com.sockywocky.createaddonorganizer.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.Gson;
import com.sockywocky.createaddonorganizer.Config;
import com.sockywocky.createaddonorganizer.createaddonorganizer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class CreditsCatalog {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation MANIFEST =
            ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID, "credits/credits.json");
    private static final ResourceLocation TEXT_BANNER_MANIFEST =
            ResourceLocation.fromNamespaceAndPath(createaddonorganizer.MODID, "credits/text_banner_credits.json");
    private static final int DEFAULT_NAME_COLOR = 0xFFFFFFFF;

    private CreditsCatalog() {}

    public record Contributor(String name, List<String> banners, String color) {}

    public record Entry(boolean header, String label, int nameColor, ResourceLocation texture, String filename) {}

    private record UnifiedContributor(String name, String color, List<String> files) {}

    public static List<Entry> rows() {
        List<UnifiedContributor> source = RemoteBanners.hasEverCached()
                ? toUnifiedBanner(RemoteBanners.contributors())
                : toUnifiedFromJar(loadJarContributors(MANIFEST));
        return buildRows(source, CreditsCatalog::resolveBanner);
    }

    public static List<Entry> textBannerRows() {
        List<UnifiedContributor> source = RemoteBoxTextures.hasEverCached()
                ? toUnifiedBox(RemoteBoxTextures.contributors())
                : toUnifiedFromJar(loadJarContributors(TEXT_BANNER_MANIFEST));
        return buildRows(source, CreditsCatalog::resolveBoxTexture);
    }

    private static List<Entry> buildRows(List<UnifiedContributor> contributors, Function<String, ResourceLocation> resolver) {
        List<Entry> out = new ArrayList<>();
        for (UnifiedContributor contributor : contributors) {
            List<ResourceLocation> textures = new ArrayList<>();
            List<String> filenames = new ArrayList<>();
            for (String filename : contributor.files()) {
                ResourceLocation texture = resolver.apply(filename);
                if (texture != null) {
                    textures.add(texture);
                    filenames.add(filename);
                } else {
                    createaddonorganizer.LOGGER.warn("[CAO] credits manifest entry for '{}' references unresolvable texture '{}'",
                            contributor.name(), filename);
                }
            }
            if (textures.isEmpty()) {
                continue;
            }
            int color = nameColor(contributor.color());
            out.add(new Entry(true, contributor.name(), color, null, null));
            for (int i = 0; i < textures.size(); i++) {
                out.add(new Entry(false, null, color, textures.get(i), filenames.get(i)));
            }
        }
        return out;
    }

    private static List<UnifiedContributor> toUnifiedFromJar(List<Contributor> contributors) {
        List<UnifiedContributor> out = new ArrayList<>(contributors.size());
        for (Contributor c : contributors) {
            out.add(new UnifiedContributor(c.name(), c.color(), c.banners()));
        }
        return out;
    }

    private static List<UnifiedContributor> toUnifiedBanner(List<RemoteBanners.RemoteContributor> contributors) {
        List<UnifiedContributor> out = new ArrayList<>(contributors.size());
        for (RemoteBanners.RemoteContributor c : contributors) {
            List<String> files = c.banners().stream().map(RemoteBanners.RemoteBannerFile::file).toList();
            out.add(new UnifiedContributor(c.name(), c.color(), files));
        }
        return out;
    }

    private static List<UnifiedContributor> toUnifiedBox(List<RemoteBoxTextures.RemoteContributor> contributors) {
        List<UnifiedContributor> out = new ArrayList<>(contributors.size());
        for (RemoteBoxTextures.RemoteContributor c : contributors) {
            List<String> files = c.textures().stream().map(RemoteBoxTextures.RemoteBoxFile::file).toList();
            out.add(new UnifiedContributor(c.name(), c.color(), files));
        }
        return out;
    }

    private static int nameColor(String color) {
        if (color == null) {
            return DEFAULT_NAME_COLOR;
        }
        Integer parsed = Config.parseColor(color);
        return parsed != null ? parsed : DEFAULT_NAME_COLOR;
    }

    private static List<Contributor> loadJarContributors(ResourceLocation manifest) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(manifest);
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Contributor[] parsed = GSON.fromJson(reader, Contributor[].class);
            return parsed != null ? List.of(parsed) : List.of();
        } catch (IOException e) {
            createaddonorganizer.LOGGER.warn("[CAO] failed to load credits manifest {}", manifest, e);
            return List.of();
        } catch (RuntimeException e) {
            createaddonorganizer.LOGGER.warn("[CAO] malformed credits manifest {}", manifest, e);
            return List.of();
        }
    }

    private static ResourceLocation resolveBanner(String filename) {
        ResourceLocation bundled = BannerTextures.resolve("res:" + createaddonorganizer.MODID + ":textures/banner/" + filename);
        if (bundled != null && bundledResourceExists(bundled)) {
            return bundled;
        }
        if (RemoteBanners.isAvailable(filename)) {
            return BannerTextures.resolve("remote:" + filename);
        }
        return null;
    }

    private static ResourceLocation resolveBoxTexture(String filename) {
        ResourceLocation bundled = BoxTextures.resolve("res:" + createaddonorganizer.MODID + ":textures/text_banner/" + filename);
        if (bundled != null && bundledResourceExists(bundled)) {
            return bundled;
        }
        if (RemoteBoxTextures.isAvailable(filename)) {
            return BoxTextures.resolve("remote:" + filename);
        }
        return null;
    }

    private static boolean bundledResourceExists(ResourceLocation texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }
}
