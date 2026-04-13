package com.example.mymod.client.render;

import com.example.mymod.entity.SparkProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Рендерер снаряда «Искра» — Багровый путь.
 *
 * Визуальный образ: раскалённое магическое ядро с вращающимися кольцами-осколками.
 *
 * Три слоя:
 *   1. Ядро — billboard-спрайт, всегда смотрит на камеру. Пульсирует по масштабу.
 *   2. Внутреннее кольцо — 4 ромба, вращаются вокруг оси Y по часовой.
 *   3. Внешние осколки — 4 вытянутых лезвия, вращаются ПРОТИВ часовой под углом 45°.
 *
 * Все части используют RenderType.eyes — всегда максимальная яркость (светятся в темноте).
 */
public class SparkProjectileRenderer extends EntityRenderer<SparkProjectile> {

    /** Текстура ядра — яркая огненная руна. */
    private static final ResourceLocation TEXTURE_CORE =
            new ResourceLocation("mymod", "textures/entity/spark_core.png");

    /** Текстура осколков-колец — горящий щербатый фрагмент. */
    private static final ResourceLocation TEXTURE_SHARD =
            new ResourceLocation("mymod", "textures/entity/spark_shard.png");

    /**
     * Максимальная яркость — снаряд светится независимо от освещения мира.
     * Пакованный формат: (blockLight=15 << 4) | (skyLight=15 << 20).
     */
    private static final int FULL_BRIGHT = 0xF000F0;

    public SparkProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(SparkProjectile entity) {
        return TEXTURE_CORE;
    }

    // ─── Главный рендер ───────────────────────────────────────────────────────

    @Override
    public void render(SparkProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        // Время в тиках с дробной частью — основа всех анимаций
        float age = entity.tickCount + partialTick;

        poseStack.pushPose();

        // Небольшое покачивание вверх-вниз при полёте (синусоида)
        float bobY = (float) Math.sin(age * 0.25f) * 0.03f;
        poseStack.translate(0.0, bobY, 0.0);

        // ─── 1. Ядро (billboard-спрайт, всегда смотрит на игрока) ───────────
        renderCore(poseStack, bufferSource, age);

        // ─── 2. Внутреннее кольцо (4 ромба, вращение по часовой) ────────────
        renderInnerRing(poseStack, bufferSource, age);

        // ─── 3. Внешние осколки (4 лезвия, вращение против часовой) ─────────
        renderOuterShards(poseStack, bufferSource, age);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // ─── Слой 1: ядро ────────────────────────────────────────────────────────

    /**
     * Рисует центральный billboard-спрайт ядра.
     * Пульсирует в диапазоне масштаба 0.85–1.0 с частотой ~1.2 Гц.
     */
    private void renderCore(PoseStack poseStack, MultiBufferSource bufferSource, float age) {
        poseStack.pushPose();

        // Billboard: поворачиваем плоскость квада навстречу камере
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        // Пульсация масштаба
        float pulse = 0.85f + (float) Math.sin(age * 0.38f) * 0.075f;
        poseStack.scale(pulse, pulse, pulse);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(TEXTURE_CORE));
        PoseStack.Pose pose = poseStack.last();
        float s = 0.3f; // половина стороны квада

        // Квад в плоскости XY (billboard уже повёрнут к камере)
        vertex(vc, pose, -s,  s, 0,  0f, 0f);
        vertex(vc, pose, -s, -s, 0,  0f, 1f);
        vertex(vc, pose,  s, -s, 0,  1f, 1f);
        vertex(vc, pose,  s,  s, 0,  1f, 0f);

        poseStack.popPose();
    }

    // ─── Слой 2: внутреннее кольцо ───────────────────────────────────────────

    /**
     * Рисует 4 ромба, вращающихся вокруг оси Y по часовой стрелке.
     * Смещены от центра на 0.25 блока, наклонены на 30° для объёмности.
     */
    private void renderInnerRing(PoseStack poseStack, MultiBufferSource bufferSource, float age) {
        float ringAngle = age * 4.5f; // ~90°/сек при 20 TPS

        VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(TEXTURE_SHARD));

        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();

            // Равномерно расставляем 4 ромба (0°, 90°, 180°, 270°)
            poseStack.mulPose(Axis.YP.rotationDegrees(ringAngle + i * 90f));
            poseStack.translate(0.25, 0.0, 0.0);

            // Наклон для объёмности — элемент не лежит плоско
            poseStack.mulPose(Axis.ZP.rotationDegrees(30f));

            float s = 0.18f;
            PoseStack.Pose pose = poseStack.last();
            vertex(vc, pose, 0,  s,  s,  0f, 0f);
            vertex(vc, pose, 0, -s,  s,  0f, 1f);
            vertex(vc, pose, 0, -s, -s,  1f, 1f);
            vertex(vc, pose, 0,  s, -s,  1f, 0f);

            poseStack.popPose();
        }
    }

    // ─── Слой 3: внешние осколки ─────────────────────────────────────────────

    /**
     * Рисует 4 вытянутых лезвия — вращаются ПРОТИВ часовой, быстрее кольца.
     * Смещены на 45° относительно кольца — два слоя создают вихревой эффект.
     */
    private void renderOuterShards(PoseStack poseStack, MultiBufferSource bufferSource, float age) {
        float shardAngle = -(age * 6.0f); // в обратную сторону, быстрее

        VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(TEXTURE_SHARD));

        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();

            // Сдвиг на 45° от внутреннего кольца
            poseStack.mulPose(Axis.YP.rotationDegrees(shardAngle + i * 90f + 45f));
            poseStack.translate(0.38, 0.0, 0.0);

            // Лезвие вытянуто по Y, наклонено — придаёт ощущение скорости
            poseStack.mulPose(Axis.ZP.rotationDegrees(60f));

            float w = 0.08f; // тонкое
            float h = 0.22f; // вытянутое
            PoseStack.Pose pose = poseStack.last();
            vertex(vc, pose, 0,  h,  w,  0f, 0f);
            vertex(vc, pose, 0, -h,  w,  0f, 1f);
            vertex(vc, pose, 0, -h, -w,  1f, 1f);
            vertex(vc, pose, 0,  h, -w,  1f, 0f);

            poseStack.popPose();
        }
    }

    // ─── Утилита ─────────────────────────────────────────────────────────────

    /**
     * Добавляет одну вершину квада с полной яркостью.
     *
     * @param vc   буфер вершин (из MultiBufferSource.getBuffer)
     * @param pose текущая матрица трансформации PoseStack
     * @param x,y,z позиция в локальном пространстве снаряда
     * @param u,v   UV-координаты текстуры (0..1)
     */
    private void vertex(VertexConsumer vc, PoseStack.Pose pose,
                        float x, float y, float z, float u, float v) {
        vc.vertex(pose.pose(), x, y, z)
          .color(255, 255, 255, 255)
          .uv(u, v)
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(FULL_BRIGHT)        // снаряд всегда ярко светится
          .normal(pose.normal(), 0f, 1f, 0f)
          .endVertex();
    }
}
