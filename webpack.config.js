const path = require('path');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const {CleanWebpackPlugin} = require('clean-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const ModuleFederationPlugin = require('webpack/lib/container/ModuleFederationPlugin');
const {CycloneDxWebpackPlugin} = require('@cyclonedx/webpack-plugin');
const getModuleFederationConfig = require('@jahia/webpack-config/getModuleFederationConfig');

const packageJson = require('./package.json');
const {ContextReplacementPlugin} = require("webpack");

/** @type {import('@cyclonedx/webpack-plugin').CycloneDxWebpackPluginOptions} */
const cycloneDxWebpackPluginOptions = {
    specVersion: '1.4',
    rootComponentType: 'library',
    outputLocation: './bom',
    validateResults: false
};

module.exports = (env, argv) => {
    let config = {
        entry: {
            main: path.resolve(__dirname, 'src/javascript/index')
        },
        output: {
            path: path.resolve(__dirname, 'src/main/resources/javascript/apps/'),
            filename: 'deepl-translation.bundle.js',
            chunkFilename: '[name].deepl-translation.[chunkhash:6].js'
        },
        resolve: {
            mainFields: ['module', 'main'],
            extensions: ['.mjs', '.js', '.jsx', '.json', '.scss'],
            alias: {
                '~': path.resolve(__dirname, './src/javascript'),
            },
            fallback: {
                "url": false,
                "fs": false, // 'fs' is typically not available in the browser and might not have a browser-friendly package
                "tls": false, // Similar to 'fs', 'tls' is node-specific and usually not required in a browser context
                "net": false, // There's no direct browser equivalent for 'net', it's node-specific
                "path": require.resolve("path-browserify"),
                "zlib": require.resolve("browserify-zlib"),
                "http": require.resolve("stream-http"),
                "https": require.resolve("https-browserify"),
                "stream": require.resolve("stream-browserify"),
                "crypto": require.resolve("crypto-browserify"),
                // For 'crypto-browserify', it seems like a redundancy since 'crypto' is already replaced with 'crypto-browserify'
                "os": require.resolve("os-browserify/browser"),
                "util": require.resolve("util/"),
                "assert": require.resolve("assert/"),
                "tty": require.resolve("tty-browserify"),
                "vm": require.resolve("vm-browserify")
            }

        },
        module: {
            rules: [
                {
                    test: /\.m?js$/,
                    type: 'javascript/auto'
                },
                {
                    test: /\.jsx?$/,
                    include: [path.join(__dirname, 'src')],
                    use: {
                        loader: 'babel-loader',
                        options: {
                            presets: [
                                ['@babel/preset-env', {
                                    modules: false,
                                    targets: {chrome: '60', edge: '44', firefox: '54', safari: '12'}
                                }],
                                '@babel/preset-react'
                            ],
                            plugins: [
                                'lodash',
                                '@babel/plugin-syntax-dynamic-import'
                            ]
                        }
                    }
                },
                {
                    test: /\.css$/,
                    use: ['style-loader', 'css-loader']
                },
                {
                    test: /\.scss$/i,
                    sideEffects: true,
                    use: [
                        'style-loader',
                        // Translates CSS into CommonJS
                        {
                            loader: 'css-loader',
                            options: {
                                modules: {
                                    mode: 'local'
                                }
                            }
                        },
                        // Compiles Sass to CSS
                        'sass-loader'
                    ]
                },
                {
                    test: /\.(png|svg)$/,
                    use: ['file-loader']
                },
                {
                    test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
                    use: [{
                        loader: 'file-loader',
                        options: {
                            name: '[name].[ext]',
                            outputPath: 'fonts/'
                        }
                    }]
                }
            ]
        },

        plugins: [
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson, {
                remotes: {
                    '@jahia/app-shell': 'appShellRemote',
                    //'@jahia/jcontent':'appShell.remotes.jcontent'
                    '@jahia/content-editor':'appShell.remotes.contentEditor'

                }
            })),
            new CleanWebpackPlugin({verbose: false}),
            new CopyWebpackPlugin([{from: './package.json', to: ''}]),
            new CycloneDxWebpackPlugin(cycloneDxWebpackPluginOptions),
            new ContextReplacementPlugin(/any-promise/)

        ],
        mode: 'development'
    };

    config.devtool = (argv.mode === 'production') ? 'source-map' : 'eval-source-map';

    if (argv.analyze) {
        config.devtool = 'source-map';
        config.plugins.push(new BundleAnalyzerPlugin());
    }

    return config;
};
