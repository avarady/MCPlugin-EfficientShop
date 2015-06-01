#EfficientShop
Allows the creation of SellAll and GUI Buy signs. This plugin requires Vault to function properly.

##Permissions
Node | Permission | Default
:---|:---|:---
eshop.place | Allows a player to place EfficientShop signs. | op
eshop.use | Allows a player to use EfficientShop signs. | true

##Usage
Each sign must have the format

`[Shop]`

`Buy` or `SellAll`

`<item>`

`<currency sign><amount>/ea`

The amount can include cents, and the item can be a name or the item ID.
The currency sign must match the currency in the config file.

If the sign is formatted correctly, it will change colors.
#####Examples:
```
[Shop]
Buy
Iron Ingot
$6.99/ea
```

```
[Shop]
SellAll
Stone
$1.00/ea
```
