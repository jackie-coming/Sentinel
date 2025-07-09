import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

/**
 * 事务状态检测工具
 */
@Component
public class TransactionChecker {
    
    /**
     * 检查当前是否在事务中
     */
    public static boolean isInTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
    
    /**
     * 检查方法是否可能导致事务失效
     */
    public static void checkMethodForTransaction(Object target, String methodName) {
        Class<?> clazz = target.getClass();
        
        // 检查类是否是final
        if (java.lang.reflect.Modifier.isFinal(clazz.getModifiers())) {
            System.out.println("⚠️  警告: " + clazz.getSimpleName() + " 是final类，事务可能失效");
        }
        
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    // 检查方法是否是final
                    if (java.lang.reflect.Modifier.isFinal(method.getModifiers())) {
                        System.out.println("⚠️  警告: 方法 " + methodName + " 是final方法，事务可能失效");
                    }
                    
                    // 检查方法是否是private
                    if (java.lang.reflect.Modifier.isPrivate(method.getModifiers())) {
                        System.out.println("⚠️  警告: 方法 " + methodName + " 是private方法，事务会失效");
                    }
                    
                    // 检查是否有@Transactional注解
                    if (method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)) {
                        System.out.println("✅ 方法 " + methodName + " 有@Transactional注解");
                    }
                    
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("检查方法时出错: " + e.getMessage());
        }
    }
    
    /**
     * 在方法执行前后检查事务状态
     */
    public static void logTransactionStatus(String methodName) {
        boolean inTransaction = isInTransaction();
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        
        System.out.println("=== " + methodName + " 事务状态 ===");
        System.out.println("事务激活: " + inTransaction);
        System.out.println("事务名称: " + transactionName);
        System.out.println("只读事务: " + readOnly);
        System.out.println("==============================");
    }
}

// 使用示例
@Service
class ExampleService {
    
    @Transactional
    public final void finalMethod() {
        TransactionChecker.logTransactionStatus("finalMethod");
        // 业务逻辑...
    }
    
    @Transactional
    public void normalMethod() {
        TransactionChecker.logTransactionStatus("normalMethod");
        // 业务逻辑...
    }
    
    // 测试方法
    public void testMethods() {
        TransactionChecker.checkMethodForTransaction(this, "finalMethod");
        TransactionChecker.checkMethodForTransaction(this, "normalMethod");
    }
} 