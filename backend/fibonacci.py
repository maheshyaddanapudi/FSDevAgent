def fibonacci(n):
    """
    Calculate the Fibonacci sequence up to the nth term.
    
    Args:
        n: The number of terms to calculate
        
    Returns:
        A list containing the Fibonacci sequence up to n terms
    """
    # Initialize the sequence with the first two Fibonacci numbers
    fib_sequence = [0, 1]
    
    # Generate the sequence up to n terms
    for i in range(2, n):
        # Next number is the sum of the previous two
        next_number = fib_sequence[i-1] + fib_sequence[i-2]
        fib_sequence.append(next_number)
    
    return fib_sequence

# Calculate Fibonacci sequence up to n=10
n = 10
result = fibonacci(n)

# Print the sequence
print(f"Fibonacci sequence up to {n} terms:")
print(result)

# Print each number in the sequence
print("\nEach number in the sequence:")
for i, num in enumerate(result):
    print(f"F({i}) = {num}")