module.exports = {
    testEnvironment: 'jsdom',
    setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
    modulePathIgnorePatterns: ['<rootDir>/target/', '<rootDir>/src/main/resources/javascript/apps/'],
    moduleNameMapper: {
        '\\.(scss|css)$': 'identity-obj-proxy'
    },
    transform: {
        '^.+\\.[jt]sx?$': ['babel-jest', {
            configFile: false,
            babelrc: false,
            presets: [
                ['@babel/preset-env', {targets: {node: 'current'}}],
                ['@babel/preset-react', {runtime: 'classic'}]
            ]
        }]
    },
    testMatch: ['**/?(*.)+(test).[jt]s?(x)'],
    collectCoverageFrom: [
        'src/javascript/DownloadHelper/**/*.{js,jsx}'
    ]
};
