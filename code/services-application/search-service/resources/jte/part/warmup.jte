This is a bit of a hack!

This class exists to let tailwind we're using these classes even though they aren't visible in the code,
as we sometimes generate classes from Java code or javascript!

<i class="text-blue-800 bg-blue-50 dark:text-blue-200 dark:bg-blue-950"></i>
<i class="text-green-800 bg-green-50 dark:text-green-200 dark:bg-green-950"></i>
<i class="text-purple-800 bg-purple-50 dark:text-purple-200 dark:bg-purple-950"></i>
<i class="text-blue-950 bg-gray-100 dark:text-blue-50 dark:bg-gray-900"></i>
<span class="hover:bg-gray-300 "></span>

<label class="suggestion group block relative">
    <input type="radio" name="suggestion" class="peer hidden" checked>
    <div class="px-4 py-2 cursor-pointer dark:peer-checked:bg-gray-700 dark:hover:bg-gray-700 peer-checked:bg-gray-300 hover:bg-gray-300 w-full">
    </div>
</label>