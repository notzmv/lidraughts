const gulp = require('gulp');
const sass = require('gulp-sass');
const sourcemaps = require('gulp-sourcemaps');
const autoprefixer = require('gulp-autoprefixer');
const sassInheritance = require('gulp-sass-inheritance')
const rename = require('gulp-rename');
const cached = require('gulp-cached');
const gulpif = require('gulp-if');
const filter = require('gulp-filter');
const fs = require('fs');

const themes = ['light', 'dark', 'transp'];

const sassOptions = {
  errLogToConsole: true,
  outputStyle: 'expanded'
};
const autoprefixerOptions = {
  // https://browserl.ist/?q=last+5+versions%2C+Firefox+ESR%2C+not+IE+<+12%2C+not+<+0.1%25%2C+not+IE_Mob+<+12
  browsers: 'last 5 versions, Firefox ESR, not IE < 12, not < 0.1%, not IE_Mob < 12'.split(', ')
};
const destination = () => gulp.dest('../public/css/');

// const sourceDir = '.';
// const buildDir = `${sourceDir}/build`;
const sourcesGlob = './*/css/**/*.scss';
const buildsGlob = './*/css/build/*.scss';

// createThemedBuilds(buildDir);

const build = () => gulp.src(sourcesGlob)
  //filter out unchanged scss files, only works when watching
  .pipe(gulpif(global.isWatching, cached('sass')))
  //find files that depend on the files that have changed
  .pipe(sassInheritance({dir: '.',debug: false}))
  //filter out internal imports (folders and files starting with "_" )
  .pipe(filter(file => !/\/_/.test(file.path) || !/^_/.test(file.relative)))
  .pipe(sourcemaps.init())
  .pipe(sass(sassOptions).on('error', sass.logError))
  .pipe(sourcemaps.write())
  .pipe(renameAs('dev'))
  .pipe(destination());

const setWatching = async () => { global.isWatching = true; };

gulp.task('css', gulp.series([
  setWatching,
  build,
  () => gulp.watch(sourcesGlob, build)
]));

gulp.task('css-dev', build);

gulp.task('css-prod', () => gulp
  .src(buildsGlob)
  .pipe(sass({
    ...sassOptions,
    ...{ outputStyle: 'compressed' }
  }).on('error', sass.logError))
  .pipe(autoprefixer(autoprefixerOptions))
  .pipe(renameAs('min'))
  .pipe(destination())
);

function renameAs(ext) {
  return rename(path => {
    path.dirname = '';
    path.basename = `lidraughts.${path.basename}.${ext}`;
    return path;
  });
}

function createThemedBuilds(buildDir) {
  const builds = fs.readdirSync(buildDir);
  builds
    .filter(fileName => fileName[0] === '_')
    .forEach(fileName => {
      themes.forEach(theme => {
        const themedName = fileName.replace(/^_(.+)\.scss$/, `$1.${theme}.scss`);
        const themedPath = `${buildDir}/${themedName}`;
        if (!fs.existsSync(themedPath)) {
          const buildName = fileName.replace(/^_(.+)\.scss$/, '$1');
          const code = `@import '../../../common/css/theme/${theme}';\n@import '${buildName}';\n`;
          console.log(`Create missing SCSS themed build: ${themedPath}`);
          fs.writeFileSync(themedPath, code);
        }
      });
    });
}