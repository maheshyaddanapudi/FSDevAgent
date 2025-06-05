def fibonacci_recursive(n):
    """
    Calculate the nth Fibonacci number using recursion.
    Warning: This is inefficient for large n due to repeated calculations.
    
    Args:
        n (int): The position in the Fibonacci sequence (0-indexed)
        
    Returns:
        int: The nth Fibonacci number
    """
    if n <= 0:
        return 0
    elif n == 1:
        return 1
    else:
        return fibonacci_recursive(n-1) + fibonacci_recursive(n-2)

def fibonacci_iterative(n):
    """
    Calculate the nth Fibonacci number using iteration.
    This is more efficient than the recursive approach.
    
    Args:
        n (int): The position in the Fibonacci sequence (0-indexed)
        
    Returns:
        int: The nth Fibonacci number
    """
    if n <= 0:
        return 0
    elif n == 1:
        return 1
    
    a, b = 0, 1
    for _ in range(2, n+1):
        a, b = b, a + b
    return b

def fibonacci_dynamic(n):
    """
    Calculate the nth Fibonacci number using dynamic programming.
    This approach stores previously calculated values.
    
    Args:
        n (int): The position in the Fibonacci sequence (0-indexed)
        
    Returns:
        int: The nth Fibonacci number
    """
    if n <= 0:
        return 0
    
    # Initialize array to store Fibonacci numbers
    fib = [0] * (n + 1)
    fib[1] = 1
    
    # Fill the array
    for i in range(2, n + 1):
        fib[i] = fib[i-1] + fib[i-2]
    
    return fib[n]

# Example usage
if __name__ == "__main__":
    n = 10
    print(f"Calculating the {n}th Fibonacci number using different methods:")
    print(f"Recursive method: {fibonacci_recursive(n)}")
    print(f"Iterative method: {fibonacci_iterative(n)}")
    print(f"Dynamic programming method: {fibonacci_dynamic(n)}")
    
    print("\nFirst 15 Fibonacci numbers:")
    for i in range(15):
        print(f"F({i}) = {fibonacci_iterative(i)}")