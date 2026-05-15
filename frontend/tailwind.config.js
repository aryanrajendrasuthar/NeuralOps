/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#f0f4ff',
          100: '#dce6fd',
          200: '#bad0fb',
          300: '#89aef8',
          400: '#5584f3',
          500: '#2f5fec',
          600: '#1e46e0',
          700: '#1937cc',
          800: '#1a2ea5',
          900: '#1b2d83',
          950: '#141f55',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
    },
  },
  plugins: [],
}
