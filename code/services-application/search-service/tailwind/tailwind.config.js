/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./resources/jte/**/*.jte",
  ],
  theme: {
    extend: {
      colors: {
        nicotine: '#f8f8ee',
        margeblue: '#3e5f6f',
        liteblue: '#0066cc',
      }
    },
    screens: {
      'xs': '440px',
      'sm': '640px',
      'md': '768px',
      'lg': '1024px',
      'xl': '1280px',
      '2xl': '1536px',
    },
  },
  plugins: [],
}