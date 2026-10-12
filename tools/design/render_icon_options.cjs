const fs = require('fs');
const path = require('path');
const sharp = require(process.env.NODE_PATH + '/sharp');
const root = path.resolve(__dirname, '../../design/icon-options');
async function main() {
  for (const name of ['option-1', 'option-2', 'option-3', 'options']) {
    await sharp(fs.readFileSync(path.join(root, name + '.svg'))).png().toFile(path.join(root, name + '.png'));
  }
}
main().catch(error => { process.stderr.write(String(error)); process.exitCode = 1; });
