#ifndef TR_H
#define TR_H

#include <QObject>
#include <QMap>

class TR : public QObject
{
    Q_OBJECT
public:
    static void loadDictionary(const QString &language);

    Q_INVOKABLE QString value(const QString &key) const;

    static TR* getInstance();
private:
    TR(QObject* parent = nullptr);
    static TR* sInstance;

    QMap<QString, QString> _dictionary;
};

#endif // TR_H
