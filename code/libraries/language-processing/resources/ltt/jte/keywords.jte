@import nu.marginalia.language.model.WordRep
@import nu.marginalia.language.model.DocumentSentence
@import nu.marginalia.language.model.LanguageDefinition
@import java.util.*
@import java.util.stream.IntStream

@param String textSample
@param LanguageDefinition language 
@param List<DocumentSentence> sentences
@param Map<String, String> tagColors
@param Collection<WordRep> tfIdfReps
@param Collection<WordRep> titleReps
@param Collection<WordRep> nameLikeReps
@param Collection<WordRep> subjectLikeReps
@param Collection<String> artifacts


<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>NLP Debug Tool</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        .sentence-boundary {
            border-left: 3px solid #3b82f6;
        }
        ruby rt {
            font-size: 0.65em;
            color: #6b7280;
        }
    </style>
</head>
<body class="bg-gray-50 min-h-screen">
<div class="container mx-auto px-4 py-8 max-w-6xl">
    <!-- Header -->
    <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">
            <i class="fas fa-microscope text-blue-600 mr-3"></i>
            Language Processing Debug Tool
        </h1>
        <p class="text-gray-600">Inspect and debug text processing pipeline components</p>
    </div>

    <!-- Input Section -->
    <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <form method="post">
        <div class="p-4 border-b border-gray-200">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">
                <i class="fas fa-edit text-green-600 mr-2"></i>
                Input Text
            </h2>

            <textarea name="textSample"
                    class="w-full p-4 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-none"
                    rows="4"
                    placeholder="Enter your text here to analyze...">${textSample}</textarea>
            <div class="flex justify-between items-center mt-3">
                <button class="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors">
                    <i class="fas fa-cog mr-2"></i>Analyze
                </button>
            </div>
        </div>
        </form>
    </div>

    <!-- Results Grid -->
    <div class="space-y-6">

        <!-- Sentence Breakdown with POS Tags -->
        <div class="bg-white rounded-lg shadow-sm border border-gray-200">
            <div class="p-4 border-b border-gray-200">
                <h2 class="text-lg font-semibold text-gray-900">
                    <i class="fas fa-list-ol text-purple-600 mr-2"></i>
                    Sentence Breakdown & POS Tags
                </h2>
                @if (language != null)
                <div class="text-sm text-gray-500 mt-1">Auto-detected: ${language.name()} (${language.isoCode()})</div>
                @endif
            </div>
            @if (sentences != null)
            @for (DocumentSentence sentence : sentences)
            <div class="p-4 space-y-4">
                <div class="sentence-boundary pl-4 py-4 rounded">
                    @for (int pos : IntStream.range(0, sentence.length()).toArray())
                        <ruby class="p-4">
                            
                            <span class="text-xl font-serif ${tagColors.get(sentence.posTags[pos])}">${sentence.wordsLowerCase[pos]}
                            
                            </span>
                            <rt>
                                ${sentence.posTags[pos]}
                                @if (sentence.isAllCaps(pos))
                                    <i class="fa-solid fa-angles-up"></i>
                                @elseif (sentence.isCapitalized(pos))
                                    <i class="fa-solid fa-arrow-up"></i>
                                @endif
                            </rt>
                        </ruby>
                        @if (sentence.isSeparatorComma(pos))
                            <i class="fa-regular fa-circle"></i>
                        @endif
                    @endfor
                </div>
            </div>
            @endfor
            @endif
        </div>

        <!-- Keywords & N-grams -->
        <div class="bg-white rounded-lg shadow-sm border border-gray-200">
            <div class="p-4 border-b border-gray-200">
                <h2 class="text-lg font-semibold text-gray-900">
                    <i class="fas fa-key text-indigo-600 mr-2"></i>
                    Keywords & N-grams
                </h2>
            </div>
            <div class="p-4">
                <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <!-- Keywords -->
                    @if (tfIdfReps != null && !tfIdfReps.isEmpty())
                    <div>
                        <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center">
                            <i class="fas fa-star text-yellow-500 mr-2"></i>
                            Keywords (TF-IDF)
                        </h3>
                        <div class="space-y-2">
                            @for (WordRep rep : tfIdfReps)
                            <div class="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span class="text-sm font-medium">${rep.word}</span>
                                @if (rep.length > 1)
                                    <span class="text-xs text-gray-600 bg-yellow-100 px-2 py-1 rounded">${rep.length}</span>
                                @endif
                            </div>
                            @endfor
                        </div>
                    </div>
                    @endif
                    @if (nameLikeReps != null && !nameLikeReps.isEmpty())
                        <div>
                            <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center">
                                <i class="fas fa-star text-yellow-500 mr-2"></i>
                                Name-Like
                            </h3>
                            <div class="space-y-2">
                                @for (WordRep rep : nameLikeReps)
                                    <div class="flex justify-between items-center p-2 bg-gray-50 rounded">
                                        <span class="text-sm font-medium">${rep.word}</span>
                                        @if (rep.length > 1)
                                        <span class="text-xs text-gray-600 bg-yellow-100 px-2 py-1 rounded">${rep.length}</span>
                                        @endif
                                    </div>
                                @endfor
                            </div>
                        </div>
                    @endif
                    @if (subjectLikeReps != null && !subjectLikeReps.isEmpty())
                        <div>
                            <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center">
                                <i class="fas fa-star text-yellow-500 mr-2"></i>
                                Subject-Like
                            </h3>
                            <div class="space-y-2">
                                @for (WordRep rep : subjectLikeReps)
                                    <div class="flex justify-between items-center p-2 bg-gray-50 rounded">
                                        <span class="text-sm font-medium">${rep.word}</span>
                                        @if (rep.length > 1)
                                            <span class="text-xs text-gray-600 bg-yellow-100 px-2 py-1 rounded">${rep.length}</span>
                                        @endif
                                    </div>
                                @endfor
                            </div>
                        </div>
                    @endif
                    @if (titleReps != null && !titleReps.isEmpty())
                        <div>
                            <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center">
                                <i class="fas fa-star text-yellow-500 mr-2"></i>
                                Title
                            </h3>
                            <div class="space-y-2">
                                @for (WordRep rep : titleReps)
                                    <div class="flex justify-between items-center p-2 bg-gray-50 rounded">
                                        <span class="text-sm font-medium">${rep.word}</span>
                                        @if (rep.length > 1)
                                            <span class="text-xs text-gray-600 bg-yellow-100 px-2 py-1 rounded">${rep.length}</span>
                                        @endif
                                    </div>
                                @endfor
                            </div>
                        </div>
                    @endif
                    @if (artifacts != null && !artifacts.isEmpty())
                        <div>
                            <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center">
                                <i class="fas fa-star text-yellow-500 mr-2"></i>
                                Title
                            </h3>
                            <div class="space-y-2">
                                @for (String word : artifacts)
                                    <div class="flex justify-between items-center p-2 bg-gray-50 rounded">
                                        <span class="text-sm font-medium">${word}</span>
                                    </div>
                                @endfor
                            </div>
                        </div>
                    @endif
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>