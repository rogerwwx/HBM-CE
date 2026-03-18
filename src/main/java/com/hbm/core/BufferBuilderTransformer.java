package com.hbm.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static com.hbm.core.HbmCorePlugin.coreLogger;
import static com.hbm.core.HbmCorePlugin.fail;

final class BufferBuilderTransformer {
    static final String TARGET = "net.minecraft.client.renderer.BufferBuilder";
    private static final String INJECTED_INTERFACE = "com/hbm/render/util/NTMBufferBuilder";

    static byte[] transform(String name, String transformedName, byte[] basicClass) {
        coreLogger.info("Patching class {} / {}", transformedName, name);

        try {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(basicClass);
            classReader.accept(classNode, 0);

            if (!classNode.interfaces.contains(INJECTED_INTERFACE)) {
                classNode.interfaces.add(INJECTED_INTERFACE);
                coreLogger.info("Injected interface {} into {}", INJECTED_INTERFACE, transformedName);
            }

            ClassWriter writer = new ClassWriter(classReader, 0);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Throwable t) {
            fail(TARGET, t);
            return basicClass;
        }
    }
}
