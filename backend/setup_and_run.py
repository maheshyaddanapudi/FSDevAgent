import os
import subprocess

# Task 1: Create directory if it doesn't exist
directory = "/tmp/test"
if not os.path.exists(directory):
    os.makedirs(directory)
    print(f"Created directory: {directory}")
else:
    print(f"Directory already exists: {directory}")

# Task 2: Create fibonacci.py file
fibonacci_script = '''
def fibonacci(n):
    fib_sequence = [0, 1]
    for i in range(2, n):
        fib_sequence.append(fib_sequence[i-1] + fib_sequence[i-2])
    return fib_sequence[:n]

# Calculate first 10 Fibonacci numbers
fib_numbers = fibonacci(10)
print("First 10 Fibonacci numbers:")
print(fib_numbers)
'''

filepath = os.path.join(directory, "fibonacci.py")
with open(filepath, "w") as f:
    f.write(fibonacci_script)
print(f"Created file: {filepath}")

# Task 3: Run the script and show output
print("\nRunning fibonacci.py...")
result = subprocess.run(["python3", filepath], capture_output=True, text=True)
print(result.stdout)