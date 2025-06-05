"""
Simple Sorting Algorithms in Python
This file demonstrates several basic sorting algorithms with explanations.
"""

def bubble_sort(arr):
    """
    Bubble Sort: A simple comparison-based sorting algorithm.
    
    Time Complexity:
    - Best Case: O(n) when the array is already sorted
    - Average Case: O(n²)
    - Worst Case: O(n²)
    
    Space Complexity: O(1) - in-place sorting
    
    How it works:
    - Repeatedly steps through the list
    - Compares adjacent elements and swaps them if they're in the wrong order
    - Continues until no swaps are needed
    """
    n = len(arr)
    # Flag to optimize if array is already sorted
    swapped = False
    
    # Traverse through all array elements
    for i in range(n):
        # Last i elements are already in place
        for j in range(0, n-i-1):
            # Traverse the array from 0 to n-i-1
            # Swap if the element found is greater than the next element
            if arr[j] > arr[j+1]:
                arr[j], arr[j+1] = arr[j+1], arr[j]
                swapped = True
        
        # If no swapping occurred in this pass, array is sorted
        if not swapped:
            break
    
    return arr

def selection_sort(arr):
    """
    Selection Sort: A simple comparison-based sorting algorithm.
    
    Time Complexity:
    - Best Case: O(n²)
    - Average Case: O(n²)
    - Worst Case: O(n²)
    
    Space Complexity: O(1) - in-place sorting
    
    How it works:
    - Divides the input into a sorted and an unsorted region
    - Repeatedly finds the minimum element from the unsorted region
    - Puts it at the beginning of the unsorted region
    """
    n = len(arr)
    
    # Traverse through all array elements
    for i in range(n):
        # Find the minimum element in the unsorted part
        min_idx = i
        for j in range(i+1, n):
            if arr[j] < arr[min_idx]:
                min_idx = j
        
        # Swap the found minimum element with the first element
        arr[i], arr[min_idx] = arr[min_idx], arr[i]
    
    return arr

def insertion_sort(arr):
    """
    Insertion Sort: A simple comparison-based sorting algorithm.
    
    Time Complexity:
    - Best Case: O(n) when the array is already sorted
    - Average Case: O(n²)
    - Worst Case: O(n²)
    
    Space Complexity: O(1) - in-place sorting
    
    How it works:
    - Builds the sorted array one item at a time
    - Takes one element from the input data in each iteration
    - Finds the location it belongs within the sorted part
    - Inserts it there
    """
    n = len(arr)
    
    # Traverse through 1 to len(arr)
    for i in range(1, n):
        key = arr[i]
        
        # Move elements of arr[0..i-1], that are greater than key,
        # to one position ahead of their current position
        j = i-1
        while j >= 0 and key < arr[j]:
            arr[j+1] = arr[j]
            j -= 1
        arr[j+1] = key
    
    return arr

def merge_sort(arr):
    """
    Merge Sort: A divide and conquer sorting algorithm.
    
    Time Complexity:
    - Best Case: O(n log n)
    - Average Case: O(n log n)
    - Worst Case: O(n log n)
    
    Space Complexity: O(n) - requires additional space
    
    How it works:
    - Divides the input array into two halves
    - Calls itself for the two halves
    - Merges the two sorted halves
    """
    if len(arr) <= 1:
        return arr
    
    # Finding the middle of the array
    mid = len(arr) // 2
    
    # Dividing the array elements
    left = arr[:mid]
    right = arr[mid:]
    
    # Recursive call on each half
    left = merge_sort(left)
    right = merge_sort(right)
    
    # Merging the sorted halves
    return merge(left, right)

def merge(left, right):
    """Helper function for merge sort that merges two sorted arrays."""
    result = []
    i = j = 0
    
    # Merge the two arrays
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            result.append(left[i])
            i += 1
        else:
            result.append(right[j])
            j += 1
    
    # Add remaining elements
    result.extend(left[i:])
    result.extend(right[j:])
    return result

def quick_sort(arr):
    """
    Quick Sort: A divide and conquer sorting algorithm.
    
    Time Complexity:
    - Best Case: O(n log n)
    - Average Case: O(n log n)
    - Worst Case: O(n²) when the pivot is always the smallest or largest element
    
    Space Complexity: O(log n) - for recursion stack
    
    How it works:
    - Picks an element as a pivot
    - Partitions the array around the pivot
    - Recursively sorts the sub-arrays
    """
    if len(arr) <= 1:
        return arr
    
    # Select pivot (here we use the middle element)
    pivot = arr[len(arr) // 2]
    
    # Partition elements
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    
    # Recursively sort sub-arrays
    return quick_sort(left) + middle + quick_sort(right)

# Example usage
if __name__ == "__main__":
    # Test array
    test_array = [64, 34, 25, 12, 22, 11, 90]
    
    print("Original array:", test_array)
    
    # Make copies for each sorting method
    bubble_arr = test_array.copy()
    selection_arr = test_array.copy()
    insertion_arr = test_array.copy()
    merge_arr = test_array.copy()
    quick_arr = test_array.copy()
    
    # Apply different sorting algorithms
    print("\nBubble Sort Result:", bubble_sort(bubble_arr))
    print("Selection Sort Result:", selection_sort(selection_arr))
    print("Insertion Sort Result:", insertion_sort(insertion_arr))
    print("Merge Sort Result:", merge_sort(merge_arr))
    print("Quick Sort Result:", quick_sort(quick_arr))
    
    # Compare with Python's built-in sort
    python_arr = test_array.copy()
    python_arr.sort()
    print("\nPython's built-in sort:", python_arr)
    
    # Performance comparison example
    print("\nNote: For real performance testing, use the timeit module or larger datasets.")