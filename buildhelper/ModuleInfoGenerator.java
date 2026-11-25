/*
 * The MIT License
 *
 * Copyright (c) 2017-2024 JOML
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import java.io.File;
import java.io.FileOutputStream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generate a Java 9 module-info.class file to the target/classes directory.
 * 
 * @author Kai Burjack
 */
public class ModuleInfoGenerator implements Opcodes {
    /**
     * Arguments: the classes directory, the version, the module's name, and optionally a comma-separated list of the
     * Vectrix modules it requires (transitively, so reading a module also reads the modules it builds on). Every
     * package under the classes directory that holds a class is exported (4.0: one module per jar).
     */
    public static void main(String[] args) throws Exception {
        File dest = new File(args[0]);
        String name = args.length > 2 ? args[2] : "org.dynamisengine.vectrix";
        String[] requires = args.length > 3 && !args[3].isBlank() ? args[3].split(",") : new String[0];
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor mv = cw.visitModule(name, 0, args[1]);
        mv.visitRequire("java.base", ACC_MANDATED, "9");
        mv.visitRequire("jdk.unsupported", ACC_STATIC_PHASE, null);
        mv.visitRequire("jdk.incubator.vector", ACC_STATIC_PHASE, null);
        mv.visitRequire("jdk.internal.vm.ci", ACC_STATIC_PHASE, null);
        for (String r : requires) {
            mv.visitRequire(r.trim(), ACC_TRANSITIVE, null);
        }
        java.util.TreeSet<String> packages = new java.util.TreeSet<>();
        collect(dest, dest, packages);
        for (String p : packages) {
            mv.visitExport(p, 0, (String[]) null);
        }
        mv.visitEnd();
        cw.visitEnd();
        try (FileOutputStream fos = new FileOutputStream(new File(dest, "module-info.class"))) {
            fos.write(cw.toByteArray());
        }
    }

    /** The internal names of the directories under root that hold a class file (other than module-info). */
    private static void collect(File root, File dir, java.util.Set<String> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                collect(root, f, out);
            } else if (f.getName().endsWith(".class") && !f.getName().equals("module-info.class") && dir != root) {
                out.add(root.toPath().relativize(dir.toPath()).toString().replace(File.separatorChar, '/'));
            }
        }
    }
}
