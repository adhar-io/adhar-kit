package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.annotation.AiEmbedding;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aspect for processing @AiEmbedding annotations.
 *
 * <p>Automatically generates embeddings for text parameters or method return values.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class AiEmbeddingAspect {

    private final AiFacade aiFacade;

    public AiEmbeddingAspect() {
        this.aiFacade = AiFacade.getInstance();
    }

    /**
     * Intercepts methods annotated with @AiEmbedding.
     */
    @Around("@annotation(aiEmbedding)")
    public Object processEmbeddingAnnotation(ProceedingJoinPoint joinPoint, AiEmbedding aiEmbedding)
            throws Throwable {

        if (!aiFacade.isAvailable()) {
            log.warn("AI provider not available, executing original method");
            return joinPoint.proceed();
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> returnType = signature.getReturnType();

            // Handle batch embedding
            if (aiEmbedding.batch()) {
                return handleBatchEmbedding(joinPoint);
            }

            // Handle single embedding
            return handleSingleEmbedding(joinPoint);

        } catch (Exception e) {
            log.error("Error processing @AiEmbedding annotation", e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles single text embedding.
     */
    private Object handleSingleEmbedding(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // Assume first String parameter is the text to embed
        for (Object arg : args) {
            if (arg instanceof String) {
                String text = (String) arg;
                log.debug("Generating embedding for text: {}",
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);
                return aiFacade.embed(text);
            }
        }

        // No string parameter found, proceed with original method
        log.warn("No String parameter found for @AiEmbedding");
        return joinPoint.proceed();
    }

    /**
     * Handles batch embedding.
     */
    @SuppressWarnings("unchecked")
    private Object handleBatchEmbedding(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // Find List<String> parameter
        for (Object arg : args) {
            if (arg instanceof List) {
                try {
                    List<String> texts = (List<String>) arg;
                    log.debug("Generating batch embeddings for {} texts", texts.size());
                    return aiFacade.embedBatch(texts);
                } catch (ClassCastException e) {
                    log.warn("List parameter is not List<String>");
                }
            }
        }

        // No list parameter found, proceed with original method
        log.warn("No List<String> parameter found for batch @AiEmbedding");
        return joinPoint.proceed();
    }
}

