/**
 * 使用 SootUp 生成 ts-user-service 的调用图 (Call Graph)
 * 
 * 使用说明：
 * 1. 确保项目已编译：mvn compile
 * 2. 确保已安装 JDK 8
 * 3. 运行此程序生成调用图
 * 
 * 输出文件：ts-user-service-callgraph.dot
 * 可以使用 Graphviz 工具将 .dot 文件转换为可视化图形：
 *   dot -Tpng ts-user-service-callgraph.dot -o callgraph.png
 */

import qilin.core.PTA;
import qilin.driver.PTAFactory;
import qilin.driver.PTAPattern;
import sootup.callgraph.*;
import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.VoidType;
import sootup.java.bytecode.frontend.inputlocation.DefaultRuntimeAnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.DownloadJarAnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class javaCallGraph {
    public static void main(String[] args) {
        // Step 1: 创建类型层次结构
        // 获取项目根目录的绝对路径
        String projectRoot = System.getProperty("user.dir");
        String classPath;
        
        // 检查是否在项目根目录或ts-user-service目录
        if (projectRoot.endsWith("ts-user-service")) {
            classPath = projectRoot + "\\target\\classes";
        } else {
            classPath = projectRoot + "\\ts-user-service\\target\\classes";
        }
        
        System.out.println("Using classpath: " + classPath);
        
        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        inputLocations.add(new JavaClassPathAnalysisInputLocation(classPath));

        // 将 target/dependency 下的依赖 JAR 加入 classpath（如果存在）
        String dependencyDir = classPath.replace("\\target\\classes", "\\target\\dependency");
        File dependencyFolder = new File(dependencyDir);
        if (dependencyFolder.exists() && dependencyFolder.isDirectory()) {
            File[] jars = dependencyFolder.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    inputLocations.add(new JavaClassPathAnalysisInputLocation(jar.getAbsolutePath()));
                }
                System.out.println("Loaded dependency jars from: " + dependencyDir);
            }
        } else {
            System.out.println("Dependency folder not found at: " + dependencyDir + " (run `mvn dependency:copy-dependencies` if needed)");
        }
        
        // JDK 8 使用 rt.jar
        // 优先尝试从系统属性获取JDK路径
        String javaHome = System.getProperty("java.home");
        boolean rtJarAdded = false;
        
        if (javaHome != null && !javaHome.isEmpty()) {
            // 尝试 jre/lib/rt.jar
            String rtJarPath = javaHome + "\\lib\\rt.jar";
            if (new java.io.File(rtJarPath).exists()) {
                inputLocations.add(new JavaClassPathAnalysisInputLocation(rtJarPath));
                rtJarAdded = true;
            } else {
                // 尝试 ../jre/lib/rt.jar (如果java.home指向jre目录)
                rtJarPath = javaHome.replace("\\jre", "") + "\\jre\\lib\\rt.jar";
                if (new java.io.File(rtJarPath).exists()) {
                    inputLocations.add(new JavaClassPathAnalysisInputLocation(rtJarPath));
                    rtJarAdded = true;
                }
            }
        }
        
        // 如果还没添加，尝试默认JDK 8路径
        if (!rtJarAdded) {
            String jdk8RtJar = "C:\\Program Files\\Java\\jdk1.8.0_161\\jre\\lib\\rt.jar";
            if (new java.io.File(jdk8RtJar).exists()) {
                inputLocations.add(new JavaClassPathAnalysisInputLocation(jdk8RtJar));
                rtJarAdded = true;
            }
        }
        
        // 如果仍然没有找到rt.jar，使用默认运行时（适用于JDK 9+）
        if (!rtJarAdded) {
            inputLocations.add(new DefaultRuntimeAnalysisInputLocation());
        }

        JavaView view = new JavaView(inputLocations);

        // Step 2: 定义入口方法 - user服务的 UserServiceImpl 业务方法
        JavaClassType serviceType = view.getIdentifierFactory().getClassType("user.service.impl.UserServiceImpl");

        List<MethodSignature> entryMethods = new ArrayList<>();
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "saveUser",
                "edu.fudan.common.util.Response",
                Arrays.asList("user.dto.UserDto", "org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "getAllUsers",
                "edu.fudan.common.util.Response",
                Collections.singletonList("org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "findByUserName",
                "edu.fudan.common.util.Response",
                Arrays.asList("java.lang.String", "org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "findByUserId",
                "edu.fudan.common.util.Response",
                Arrays.asList("java.lang.String", "org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "deleteUser",
                "edu.fudan.common.util.Response",
                Arrays.asList("java.lang.String", "org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "updateUser",
                "edu.fudan.common.util.Response",
                Arrays.asList("user.dto.UserDto", "org.springframework.http.HttpHeaders")
        ));
        entryMethods.add(view.getIdentifierFactory().getMethodSignature(
                serviceType,
                "deleteUserAuth",
                "void",
                Arrays.asList("java.lang.String", "org.springframework.http.HttpHeaders")
        ));


        // Step 3: 生成调用图（选择一种算法）
        // 3.1 使用 CHA 算法
//        CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);
//        CallGraph cgCHA = cha.initialize(Collections.singletonList(entryMethodSignature));
//        System.out.println("CHA Call Graph:");
//        cgCHA.callsFrom(entryMethodSignature).forEach(tgt ->
//                System.out.println(entryMethodSignature + " may call " + tgt));

//         3.2 使用 RTA 算法
        CallGraphAlgorithm rta = new RapidTypeAnalysisAlgorithm(view);
        CallGraph cgRTA = rta.initialize(entryMethods);
        System.out.println("\nRTA Call Graph:");
        entryMethods.forEach(entry ->
                cgRTA.callsFrom(entry).forEach(tgt ->
                        System.out.println(entry + " may call " + tgt))
        );

//        // 3.3 使用 Qilin 指针分析（可选）
//        PTAPattern ptaPattern = new PTAPattern("insens"); // Context-insensitive
//        PTA pta = PTAFactory.createPTA(ptaPattern, view, mainClassName);
//        pta.run();
//        CallGraph cgQilin = pta.getCallGraph();
//        System.out.println("\nQilin Call Graph:");
//        cgQilin.callsFrom(entryMethodSignature).forEach(tgt ->
//                System.out.println(entryMethodSignature + " may call " + tgt));

        // 步骤 6：导出 DOT 文件
//        try (java.io.PrintWriter writer = new java.io.PrintWriter("callgraph.dot")) {
//            writer.println("digraph CallGraph {");
//            // 遍历所有方法，获取调用关系
//            view.getClasses().forEach(sootClass -> {
//                sootClass.getMethods().forEach(sourceMethod -> {
//                    MethodSignature sourceSig = sourceMethod.getSignature();
//                    cgCHA.callsFrom(sourceSig).forEach(targetSig -> {
//                        writer.println("  \"" + sourceSig + "\" -> \"" + targetSig + "\"");
//                    });
//                });
//            });
//            writer.println("}");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


//        try (PrintWriter writer = new PrintWriter("callgraph.dot")) {
//            writer.println("digraph CallGraph {");
//            // 遍历所有类和方法，但添加检查
//            view.getClasses().forEach(sootClass -> {
//                sootClass.getMethods().forEach(sourceMethod -> {
//                    MethodSignature sourceSig = sourceMethod.getSignature();
//                    // 添加检查：只处理调用图中存在的源方法
//                    if (cgCHA.containsMethod(sourceSig)) {
//                        cgCHA.callsFrom(sourceSig).forEach(targetSig -> {
//                            writer.println("  \"" + sourceSig + "\" -> \"" + targetSig + "\"");
//                        });
//                    }
//                });
//            });
//            writer.println("}");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        // 步骤 4：导出 DOT 文件
        String dotContent = ((GraphBasedCallGraph) cgRTA).exportAsDot();

        try (PrintWriter writer = new PrintWriter("ts-user-service-callgraph.dot")) {
            writer.write(dotContent);
            System.out.println("\nCall graph exported to: ts-user-service-callgraph.dot");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}