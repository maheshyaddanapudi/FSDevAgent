// PerformanceOptimizations.js - Issue #7: Performance Optimizations
import { useCallback, useEffect, useRef, useState, useMemo } from 'react';

// Issue #7 Fix: Advanced throttling with dynamic adjustment
export const useAdvancedThrottle = (callback, delay, options = {}) => {
  const {
    leading = true,
    trailing = true,
    maxWait = delay * 2,
    adaptive = false
  } = options;

  const timeoutRef = useRef(null);
  const maxTimeoutRef = useRef(null);
  const lastCallTimeRef = useRef(0);
  const lastExecTimeRef = useRef(0);
  const argsRef = useRef(null);
  const executionTimesRef = useRef([]);

  // Adaptive delay adjustment based on execution performance
  const getAdaptiveDelay = useCallback(() => {
    if (!adaptive || executionTimesRef.current.length < 5) return delay;
    
    const avgExecutionTime = executionTimesRef.current.reduce((a, b) => a + b, 0) / executionTimesRef.current.length;
    
    // Increase delay if function is taking longer to execute
    if (avgExecutionTime > 50) return delay * 1.5;
    if (avgExecutionTime > 100) return delay * 2;
    
    return delay;
  }, [delay, adaptive]);

  const execute = useCallback(() => {
    const startTime = performance.now();
    
    try {
      const result = callback.apply(null, argsRef.current);
      
      if (adaptive) {
        const executionTime = performance.now() - startTime;
        executionTimesRef.current.push(executionTime);
        
        // Keep only last 10 execution times
        if (executionTimesRef.current.length > 10) {
          executionTimesRef.current.shift();
        }
      }
      
      lastExecTimeRef.current = Date.now();
      return result;
    } catch (error) {
      console.error('Error in throttled function:', error);
      throw error;
    }
  }, [callback, adaptive]);

  const throttledFunction = useCallback((...args) => {
    const currentTime = Date.now();
    argsRef.current = args;
    lastCallTimeRef.current = currentTime;

    const timeSinceLastExec = currentTime - lastExecTimeRef.current;
    const currentDelay = getAdaptiveDelay();

    // Leading edge execution
    if (leading && timeSinceLastExec >= currentDelay) {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
      if (maxTimeoutRef.current) {
        clearTimeout(maxTimeoutRef.current);
        maxTimeoutRef.current = null;
      }
      return execute();
    }

    // Cancel existing timeout
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    // Set up trailing execution
    if (trailing) {
      timeoutRef.current = setTimeout(() => {
        timeoutRef.current = null;
        if (maxTimeoutRef.current) {
          clearTimeout(maxTimeoutRef.current);
          maxTimeoutRef.current = null;
        }
        execute();
      }, currentDelay);
    }

    // Set up max wait timeout
    if (maxWait && !maxTimeoutRef.current) {
      maxTimeoutRef.current = setTimeout(() => {
        if (timeoutRef.current) {
          clearTimeout(timeoutRef.current);
          timeoutRef.current = null;
        }
        maxTimeoutRef.current = null;
        execute();
      }, maxWait);
    }
  }, [execute, leading, trailing, getAdaptiveDelay, maxWait]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (maxTimeoutRef.current) clearTimeout(maxTimeoutRef.current);
    };
  }, []);

  // Cancel method
  throttledFunction.cancel = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
    if (maxTimeoutRef.current) {
      clearTimeout(maxTimeoutRef.current);
      maxTimeoutRef.current = null;
    }
  };

  // Flush method (execute immediately)
  throttledFunction.flush = () => {
    if (timeoutRef.current || maxTimeoutRef.current) {
      throttledFunction.cancel();
      execute();
    }
  };

  return throttledFunction;
};

// Issue #7 Fix: Dimension change detection with efficient monitoring
export const useDimensionObserver = (ref, callback, options = {}) => {
  const {
    throttleDelay = 100,
    threshold = 5, // Minimum pixel change to trigger callback
    debounceDelay = 50
  } = options;

  const lastDimensionsRef = useRef({ width: 0, height: 0 });
  const observerRef = useRef(null);
  const timeoutRef = useRef(null);

  const throttledCallback = useAdvancedThrottle(callback, throttleDelay, {
    leading: true,
    trailing: true,
    adaptive: true
  });

  const checkDimensions = useCallback(() => {
    if (!ref.current) return;

    const rect = ref.current.getBoundingClientRect();
    const newDimensions = {
      width: Math.round(rect.width),
      height: Math.round(rect.height)
    };

    const lastDims = lastDimensionsRef.current;
    const widthChanged = Math.abs(newDimensions.width - lastDims.width) >= threshold;
    const heightChanged = Math.abs(newDimensions.height - lastDims.height) >= threshold;

    if (widthChanged || heightChanged) {
      lastDimensionsRef.current = newDimensions;
      
      // Debounce rapid changes
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      
      timeoutRef.current = setTimeout(() => {
        throttledCallback(newDimensions, lastDims);
      }, debounceDelay);
    }
  }, [ref, throttledCallback, threshold, debounceDelay]);

  useEffect(() => {
    if (!ref.current) return;

    // Use ResizeObserver if available, fallback to MutationObserver
    if (window.ResizeObserver) {
      observerRef.current = new ResizeObserver((entries) => {
        for (const entry of entries) {
          checkDimensions();
        }
      });

      observerRef.current.observe(ref.current);
    } else {
      // Fallback for older browsers
      const handleResize = () => checkDimensions();
      window.addEventListener('resize', handleResize);
      
      // Also monitor for DOM changes that might affect size
      if (window.MutationObserver) {
        observerRef.current = new MutationObserver(handleResize);
        observerRef.current.observe(ref.current, {
          attributes: true,
          childList: true,
          subtree: true,
          attributeFilter: ['style', 'class']
        });
      }

      return () => {
        window.removeEventListener('resize', handleResize);
        if (observerRef.current) {
          observerRef.current.disconnect();
        }
      };
    }

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [ref, checkDimensions]);

  // Initial dimension check
  useEffect(() => {
    checkDimensions();
  }, [checkDimensions]);
};

// Issue #7 Fix: Virtual scrolling for large datasets
export const useVirtualScrolling = (items, itemHeight, containerHeight, options = {}) => {
  const {
    overscan = 5,
    threshold = 10
  } = options;

  const [scrollTop, setScrollTop] = useState(0);
  const [containerRef, setContainerRef] = useState(null);

  const visibleItems = useMemo(() => {
    if (!items || !Array.isArray(items) || items.length === 0) {
      return { items: [], startIndex: 0, endIndex: 0 };
    }

    const visibleCount = Math.ceil(containerHeight / itemHeight);
    const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
    const endIndex = Math.min(items.length - 1, startIndex + visibleCount + overscan * 2);

    return {
      items: items.slice(startIndex, endIndex + 1),
      startIndex,
      endIndex,
      totalHeight: items.length * itemHeight,
      offsetY: startIndex * itemHeight
    };
  }, [items, itemHeight, containerHeight, scrollTop, overscan]);

  const throttledScrollHandler = useAdvancedThrottle((e) => {
    setScrollTop(e.target.scrollTop);
  }, 16, { leading: true, trailing: true }); // ~60fps

  const scrollToIndex = useCallback((index) => {
    if (containerRef && index >= 0 && index < items.length) {
      const targetScrollTop = index * itemHeight;
      containerRef.scrollTop = targetScrollTop;
      setScrollTop(targetScrollTop);
    }
  }, [containerRef, items.length, itemHeight]);

  return {
    visibleItems,
    scrollToIndex,
    onScroll: throttledScrollHandler,
    setContainerRef,
    totalHeight: visibleItems.totalHeight
  };
};

// Export simplified versions of the most important utilities
export default {
  useAdvancedThrottle,
  useDimensionObserver,
  useVirtualScrolling
};
