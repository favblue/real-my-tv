import re

with open('app/src/main/java/com/lizongying/mytv0/SettingFragment.kt', 'r') as f:
    content = f.read()

content = re.sub(r'binding\.checkVersion\.setOnClickListener \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'binding\.appreciate\.setOnClickListener \{.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'binding\.clear\.setOnClickListener \{.*?\n\s*\}', '', content, flags=re.DOTALL)

content = content.replace('binding.clear,', '')
content = content.replace('binding.checkVersion,', '')
content = content.replace('binding.appreciate,', '')

with open('app/src/main/java/com/lizongying/mytv0/SettingFragment.kt', 'w') as f:
    f.write(content)
